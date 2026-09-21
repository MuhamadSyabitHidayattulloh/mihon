package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.engine.GeminiTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslateEngine
import eu.kanade.tachiyomi.data.translation.engine.MlKitTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngine
import eu.kanade.tachiyomi.data.translation.modelmanager.TranslationModelManager
import eu.kanade.tachiyomi.data.translation.pipeline.CanvasTextRenderer
import eu.kanade.tachiyomi.data.translation.pipeline.DefaultInpaintingEngine
import eu.kanade.tachiyomi.data.translation.pipeline.DefaultOcrEngine
import eu.kanade.tachiyomi.data.translation.pipeline.DefaultTextDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.model.TranslationMetadata
import tachiyomi.domain.translation.model.TranslationStage
import tachiyomi.domain.translation.model.TranslationState
import tachiyomi.domain.translation.service.TranslationPreferences

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val translationPreferences: TranslationPreferences,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _states = MutableStateFlow<Map<Long, TranslationState>>(emptyMap())
    val states: StateFlow<Map<Long, TranslationState>> = _states.asStateFlow()

    private val textDetector = DefaultTextDetector()
    private val ocrEngine = DefaultOcrEngine()
    private val inpaintingEngine = DefaultInpaintingEngine()
    private val canvasTextRenderer = CanvasTextRenderer()
    val modelManager = TranslationModelManager(context)

    fun getTranslationState(chapterId: Long): TranslationState {
        return _states.value[chapterId] ?: TranslationState.NotTranslated
    }

    fun isChapterTranslated(chapter: Chapter, manga: Manga): Boolean {
        val source = sourceManager.get(manga.source) ?: return false
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        ) ?: return false

        val translationsDir = chapterDir.findFile("translations")
        return translationsDir != null && translationsDir.exists() &&
            (translationsDir.listFiles()?.isNotEmpty() == true)
    }

    fun startTranslation(chapter: Chapter, manga: Manga) {
        val chapterId = chapter.id
        _states.value = _states.value + (chapterId to TranslationState.Queued)

        scope.launch {
            try {
                processChapter(chapter, manga)
            } catch (e: Exception) {
                _states.value = _states.value + (
                    chapterId to TranslationState.Failed(
                        stage = TranslationStage.DETECTION,
                        message = e.message ?: "Unknown error",
                        exception = e,
                    )
                    )
            }
        }
    }

    fun cancelTranslation(chapterId: Long) {
        _states.value = _states.value + (chapterId to TranslationState.Cancelled)
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga) {
        val source = sourceManager.get(manga.source) ?: return
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        ) ?: return

        val translationsDir = chapterDir.findFile("translations")
        translationsDir?.delete()
        _states.value = _states.value - chapter.id
    }

    private suspend fun processChapter(chapter: Chapter, manga: Manga) = withContext(Dispatchers.IO) {
        val chapterId = chapter.id
        val source = sourceManager.get(manga.source) ?: throw IllegalStateException("Source not found")
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        ) ?: throw IllegalStateException("Chapter dir not found")

        val translationsDir = chapterDir.createDirectory("translations")
            ?: throw IllegalStateException("Could not create translations dir")

        val pageFiles = chapterDir.listFiles()
            ?.filter { it.isFile && it.name?.endsWith(".json") == false && it.name != "translations" }
            ?.sortedBy { it.name } ?: emptyList()

        if (pageFiles.isEmpty()) {
            _states.value = _states.value + (
                chapterId to TranslationState.Failed(
                    stage = TranslationStage.STORAGE,
                    message = "No pages found in chapter",
                )
                )
            return@withContext
        }

        val totalPages = pageFiles.size
        val engine = getEngine()
        val sourceLang = translationPreferences.sourceLanguage.get()
        val targetLang = translationPreferences.targetLanguage.get()

        for ((index, pageFile) in pageFiles.withIndex()) {
            if (_states.value[chapterId] == TranslationState.Cancelled) return@withContext

            val pageNum = index + 1
            updateState(chapterId, TranslationStage.DETECTION, pageNum, totalPages)

            val inputStream = context.contentResolver.openInputStream(pageFile.uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) continue

            val regions = textDetector.detectTextRegions(bitmap)

            updateState(chapterId, TranslationStage.OCR, pageNum, totalPages)
            val ocrBlocks = ocrEngine.recognizeText(bitmap, regions)

            updateState(chapterId, TranslationStage.TRANSLATION, pageNum, totalPages)
            val translatedBlocks = ocrBlocks.map { block ->
                val translatedText = engine.translate(block.text, sourceLang, targetLang)
                block to translatedText
            }

            updateState(chapterId, TranslationStage.INPAINTING, pageNum, totalPages)
            val inpaintedBitmap = inpaintingEngine.inpaint(bitmap, regions)

            updateState(chapterId, TranslationStage.RENDERING, pageNum, totalPages)
            val finalBitmap = canvasTextRenderer.renderTranslatedText(inpaintedBitmap, translatedBlocks)

            updateState(chapterId, TranslationStage.STORAGE, pageNum, totalPages)
            val destFile = translationsDir.createFile(pageFile.name ?: "page_$pageNum.jpg")
            destFile?.let { file ->
                val out = context.contentResolver.openOutputStream(file.uri)
                if (out != null) {
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.close()
                }
            }
            bitmap.recycle()
            finalBitmap.recycle()
        }

        val metadata = TranslationMetadata(
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            translatorEngine = translationPreferences.translatorEngine.get(),
            font = translationPreferences.readerFont.get(),
        )
        val metadataFile = translationsDir.createFile("metadata.json")
        metadataFile?.let { file ->
            val out = context.contentResolver.openOutputStream(file.uri)
            out?.use { it.write(Json.encodeToString(metadata).toByteArray()) }
        }

        _states.value = _states.value + (chapterId to TranslationState.Completed)
    }

    private fun updateState(chapterId: Long, stage: TranslationStage, currentPage: Int, totalPages: Int) {
        val progress = currentPage.toFloat() / totalPages
        _states.value = _states.value + (
            chapterId to TranslationState.Processing(
                stage = stage,
                currentPage = currentPage,
                totalPages = totalPages,
                progress = progress,
            )
            )
    }

    private fun getEngine(): TranslationEngine {
        return when (translationPreferences.translatorEngine.get()) {
            "google" -> GoogleTranslateEngine(okHttpClient)
            "gemini" -> GeminiTranslationEngine(
                okHttpClient = okHttpClient,
                apiKey = translationPreferences.geminiApiKey.get(),
                model = translationPreferences.geminiModel.get(),
            )
            "openrouter" -> OpenRouterTranslationEngine(
                okHttpClient = okHttpClient,
                apiKey = translationPreferences.openRouterApiKey.get(),
                model = translationPreferences.openRouterModel.get(),
            )
            else -> MlKitTranslationEngine()
        }
    }
}
