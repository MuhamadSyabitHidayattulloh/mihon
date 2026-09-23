package eu.kanade.tachiyomi.data.translation

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.engine.GeminiTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.MlKitTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngine
import eu.kanade.tachiyomi.data.translation.pipeline.CanvasRenderer
import eu.kanade.tachiyomi.data.translation.pipeline.ImageCleaner
import eu.kanade.tachiyomi.data.translation.pipeline.TextDetector
import eu.kanade.tachiyomi.data.translation.pipeline.TextOcr
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.BufferedOutputStream
import java.io.InputStream

private data class PageInput(
    val name: String,
    val openStream: () -> InputStream,
)

enum class TranslationState {
    NOT_TRANSLATED,
    QUEUE,
    TRANSLATING,
    TRANSLATED,
    ERROR,
}

enum class TranslationStage {
    DETECTION,
    CLEANING,
    TRANSLATION,
    RENDERER,
}

data class TranslationProgress(
    val chapterId: Long,
    val donePages: Int = 0,
    val queuePages: Int = 0,
    val failedPages: Int = 0,
    val totalPages: Int = 0,
    val currentStage: TranslationStage = TranslationStage.DETECTION,
    val logs: List<String> = emptyList(),
)

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val translationPreferences: TranslationPreferences,
    private val modelManager: TranslationModelManager,
    private val networkHelper: NetworkHelper,
) {
    private val okHttpClient get() = networkHelper.client

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _chapterStates = MutableStateFlow<Map<Long, TranslationState>>(emptyMap())
    val chapterStates: StateFlow<Map<Long, TranslationState>> = _chapterStates.asStateFlow()

    private val _progresses = MutableStateFlow<Map<Long, TranslationProgress>>(emptyMap())
    val progresses: StateFlow<Map<Long, TranslationProgress>> = _progresses.asStateFlow()

    private val textDetector = TextDetector()
    private val textOcr = TextOcr(context)
    private val imageCleaner = ImageCleaner()
    private val canvasRenderer = CanvasRenderer()

    suspend fun getTranslationState(chapterId: Long, chapter: Chapter, manga: Manga): TranslationState {
        val currentState = _chapterStates.value[chapterId]
        if (currentState != null && currentState != TranslationState.NOT_TRANSLATED) {
            return currentState
        }

        if (isTranslationDownloaded(chapter, manga)) {
            return TranslationState.TRANSLATED
        }
        return TranslationState.NOT_TRANSLATED
    }

    suspend fun isTranslationDownloaded(chapter: Chapter, manga: Manga): Boolean {
        val chapterDir = getChapterDir(chapter, manga) ?: return false
        val translationsDir = getTranslationsDir(chapterDir) ?: return false
        val files = translationsDir.listFiles() ?: return false
        return files.any { !it.isDirectory && (it.name?.endsWith(".jpg") == true || it.name?.endsWith(".png") == true) }
    }

    fun getTranslationsDir(chapterDir: UniFile): UniFile? {
        return if (chapterDir.isDirectory) {
            chapterDir.findFile("translations") ?: chapterDir.createDirectory("translations")
        } else {
            // If chapter file is a CBZ archive
            val parent = chapterDir.parentFile ?: return null
            val folderName = "${chapterDir.name}_translations"
            val cbzTransDir = parent.findFile(folderName) ?: parent.createDirectory(folderName)
            cbzTransDir?.findFile("translations") ?: cbzTransDir?.createDirectory("translations")
        }
    }

    private suspend fun getChapterDir(chapter: Chapter, manga: Manga): UniFile? {
        val source = sourceManager.getOrStub(manga.source)
        return downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        )
    }

    fun startTranslation(chapter: Chapter, manga: Manga) {
        val chapterId = chapter.id
        _chapterStates.update { it + (chapterId to TranslationState.QUEUE) }
        _progresses.update {
            it + (
                chapterId to TranslationProgress(
                    chapterId = chapterId,
                    logs = listOf("Enqueued translation for chapter ${chapter.name}"),
                )
                )
        }

        scope.launch {
            processChapter(chapter, manga)
        }
    }

    private suspend fun processChapter(chapter: Chapter, manga: Manga) {
        val chapterId = chapter.id
        _chapterStates.update { it + (chapterId to TranslationState.TRANSLATING) }

        val chapterDir = getChapterDir(chapter, manga)
        if (chapterDir == null) {
            log(chapterId, "Error: Chapter directory not found.")
            _chapterStates.update { it + (chapterId to TranslationState.ERROR) }
            return
        }

        val translationsDir = getTranslationsDir(chapterDir)
        if (translationsDir == null) {
            log(chapterId, "Error: Could not create translations directory.")
            _chapterStates.update { it + (chapterId to TranslationState.ERROR) }
            return
        }

        val pageInputs: List<PageInput> = if (chapterDir.isDirectory) {
            chapterDir.listFiles()?.filter { file ->
                !file.isDirectory && file.name != "translations" &&
                    (
                        file.name?.endsWith(".jpg") == true || file.name?.endsWith(".png") == true ||
                            file.name?.endsWith(".webp") == true
                        )
            }?.map { file ->
                PageInput(file.name ?: "page.jpg") { file.openInputStream() }
            } ?: emptyList()
        } else {
            try {
                val reader = chapterDir.archiveReader(context)
                reader.useEntries { entries ->
                    entries.filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .map { entry ->
                            PageInput(entry.name.substringAfterLast('/')) {
                                reader.getInputStream(entry.name)!!
                            }
                        }
                        .toList()
                }
            } catch (e: Exception) {
                log(chapterId, "Error opening archive: ${e.localizedMessage}")
                emptyList()
            }
        }

        val total = pageInputs.size.coerceAtLeast(1)
        updateProgress(chapterId) {
            it.copy(
                totalPages = total,
                queuePages = total,
                donePages = 0,
                failedPages = 0,
            )
        }

        log(chapterId, "Found $total pages to translate.")

        val engine = createEngine()
        val srcLang = translationPreferences.sourceLanguage.get()
        val tgtLang = translationPreferences.targetLanguage.get()

        val detectorModelFile = modelManager.getModelFile(TranslationModelManager.KEY_DETECTOR)
        val ocrModelFile = modelManager.getModelFile(TranslationModelManager.KEY_OCR)
        val inpaintingModelFile = modelManager.getModelFile(TranslationModelManager.KEY_INPAINTING)

        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions()

        val detectorSession: OrtSession? = detectorModelFile?.takeIf { it.exists() && it.length() > 0 }?.let {
            try {
                env.createSession(it.absolutePath, sessionOptions)
            } catch (_: Throwable) {
                null
            }
        }
        val ocrSession: OrtSession? = ocrModelFile?.takeIf { it.exists() && it.length() > 0 }?.let {
            try {
                env.createSession(it.absolutePath, sessionOptions)
            } catch (_: Throwable) {
                null
            }
        }
        val inpaintingSession: OrtSession? = inpaintingModelFile?.takeIf { it.exists() && it.length() > 0 }?.let {
            try {
                env.createSession(it.absolutePath, sessionOptions)
            } catch (_: Throwable) {
                null
            }
        }

        var doneCount = 0
        var failedCount = 0

        try {
            for ((index, pageInput) in pageInputs.withIndex()) {
                val pageName = pageInput.name
                log(chapterId, "Processing page ${index + 1}/$total: $pageName")

                try {
                    // 1. Detection
                    updateProgress(chapterId) { it.copy(currentStage = TranslationStage.DETECTION) }
                    val inputStream: InputStream = pageInput.openStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap == null) {
                        log(chapterId, "Failed to decode image $pageName")
                        failedCount++
                        continue
                    }

                    val detectedBlocks = textDetector.detect(bitmap, detectorModelFile, detectorSession)
                    log(chapterId, "Page ${index + 1}: Detected ${detectedBlocks.size} text areas.")

                    // 2. OCR
                    val textBlocks = textOcr.recognize(bitmap, detectedBlocks, ocrModelFile, ocrSession)

                    // 3. Translation
                    updateProgress(chapterId) { it.copy(currentStage = TranslationStage.TRANSLATION) }
                    val originalTexts = textBlocks.map { it.originalText }
                    val translatedTexts = if (originalTexts.isNotEmpty()) {
                        engine.translate(originalTexts, srcLang, tgtLang)
                    } else {
                        emptyList()
                    }

                    for (i in textBlocks.indices) {
                        textBlocks[i].translatedText = translatedTexts.getOrElse(i) { textBlocks[i].originalText }
                    }

                    // 4. Cleaning
                    updateProgress(chapterId) { it.copy(currentStage = TranslationStage.CLEANING) }
                    val cleanedBitmap = imageCleaner.clean(bitmap, textBlocks, inpaintingModelFile, inpaintingSession)

                    // 5. Renderer
                    updateProgress(chapterId) { it.copy(currentStage = TranslationStage.RENDERER) }
                    val renderedBitmap = canvasRenderer.render(cleanedBitmap, textBlocks)

                    // Save
                    val outputFile = translationsDir.createFile(pageName)
                    if (outputFile != null) {
                        val outStream = BufferedOutputStream(outputFile.openOutputStream())
                        renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
                        outStream.flush()
                        outStream.close()
                        doneCount++
                        log(chapterId, "Page ${index + 1} translation saved.")
                    } else {
                        failedCount++
                        log(chapterId, "Error saving translated image $pageName")
                    }
                } catch (e: Exception) {
                    failedCount++
                    log(chapterId, "Error on page ${index + 1}: ${e.localizedMessage}")
                }

                updateProgress(chapterId) {
                    it.copy(
                        donePages = doneCount,
                        failedPages = failedCount,
                        queuePages = (total - doneCount - failedCount).coerceAtLeast(0),
                    )
                }
            }
        } finally {
            try {
                detectorSession?.close()
            } catch (_: Throwable) {}
            try {
                ocrSession?.close()
            } catch (_: Throwable) {}
            try {
                inpaintingSession?.close()
            } catch (_: Throwable) {}
        }

        if (doneCount > 0 || total == 0) {
            _chapterStates.update { it + (chapterId to TranslationState.TRANSLATED) }
            log(chapterId, "Translation completed successfully for ${chapter.name}.")
        } else {
            _chapterStates.update { it + (chapterId to TranslationState.ERROR) }
            log(chapterId, "Translation failed for ${chapter.name}.")
        }
    }

    suspend fun deleteTranslation(chapter: Chapter, manga: Manga) {
        val chapterId = chapter.id
        val chapterDir = getChapterDir(chapter, manga)
        if (chapterDir != null) {
            val translationsDir = getTranslationsDir(chapterDir)
            translationsDir?.delete()
        }
        _chapterStates.update { it + (chapterId to TranslationState.NOT_TRANSLATED) }
        _progresses.update { it - chapterId }
    }

    private fun createEngine(): TranslationEngine {
        return when (translationPreferences.translatorType.get()) {
            "google" -> GoogleTranslationEngine(okHttpClient)
            "gemini" -> GeminiTranslationEngine(
                okHttpClient,
                translationPreferences.geminiApiKey.get(),
                translationPreferences.geminiModel.get(),
            )
            "openrouter" -> OpenRouterTranslationEngine(
                okHttpClient,
                translationPreferences.openRouterApiKey.get(),
                translationPreferences.openRouterModel.get(),
            )
            else -> MlKitTranslationEngine()
        }
    }

    private fun log(chapterId: Long, message: String) {
        updateProgress(chapterId) {
            it.copy(logs = it.logs + message)
        }
    }

    private fun updateProgress(chapterId: Long, transform: (TranslationProgress) -> TranslationProgress) {
        _progresses.update { map ->
            val current = map[chapterId] ?: TranslationProgress(chapterId = chapterId)
            map + (chapterId to transform(current))
        }
    }
}
