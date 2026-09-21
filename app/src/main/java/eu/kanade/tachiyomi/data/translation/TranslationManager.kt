package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.translation.engine.CanvasRenderer
import eu.kanade.tachiyomi.data.translation.engine.OcrEngine
import eu.kanade.tachiyomi.data.translation.engine.TextCleaner
import eu.kanade.tachiyomi.data.translation.engine.TextDetector
import eu.kanade.tachiyomi.data.translation.engine.TranslatorEngine
import eu.kanade.tachiyomi.data.translation.model.ChapterTranslation
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.service.TranslationPreferences
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
    private val preferences: TranslationPreferences,
    networkHelper: NetworkHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translatorEngine = TranslatorEngine(preferences, networkHelper.client)
    private val ocrEngine = OcrEngine()
    private val textCleaner = TextCleaner()
    private val canvasRenderer = CanvasRenderer(preferences)

    private val translationsMap = ConcurrentHashMap<Long, ChapterTranslation>()
    private val _queueState = MutableStateFlow<List<ChapterTranslation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    fun getChapterTranslation(chapterId: Long): ChapterTranslation? {
        return translationsMap[chapterId]
    }

    fun isTranslationDownloaded(manga: Manga, chapter: Chapter): Boolean {
        val source = runBlocking { sourceManager.get(manga.source) } ?: return false
        val chapterDir = downloadManager.findChapterDir(chapter, manga, source) ?: return false

        val translationsDir = chapterDir.findFile("translations") ?: return false
        return translationsDir.exists() && (translationsDir.listFiles()?.isNotEmpty() == true)
    }

    fun translateChapter(manga: Manga, chapter: Chapter) {
        val existing = translationsMap[chapter.id]
        if (existing != null &&
            (existing.state == ChapterTranslation.State.QUEUE || existing.state == ChapterTranslation.State.TRANSLATING)
        ) {
            return
        }

        val translation = ChapterTranslation(manga, chapter).apply {
            state = ChapterTranslation.State.QUEUE
        }
        translationsMap[chapter.id] = translation
        updateQueue()

        scope.launch {
            processTranslation(translation)
        }
    }

    fun cancelTranslation(chapterId: Long) {
        val item = translationsMap[chapterId]
        if (item != null) {
            item.state = ChapterTranslation.State.NOT_TRANSLATED
            translationsMap.remove(chapterId)
            updateQueue()
        }
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter) {
        scope.launch {
            val source = sourceManager.get(manga.source) ?: return@launch
            val chapterDir = downloadManager.findChapterDir(chapter, manga, source)
            chapterDir?.findFile("translations")?.delete()
            translationsMap.remove(chapter.id)
            updateQueue()
        }
    }

    private suspend fun processTranslation(translation: ChapterTranslation) {
        translation.state = ChapterTranslation.State.TRANSLATING
        updateQueue()

        try {
            val source = sourceManager.get(translation.manga.source)
                ?: throw Exception("Source not found")
            val chapterDir = downloadManager.findChapterDir(translation.chapter, translation.manga, source)
                ?: throw Exception("Chapter directory not found")

            val filesList: Array<UniFile> = chapterDir.listFiles() ?: emptyArray()
            val files = filesList.filter { file ->
                file.isFile && (
                    file.name?.let { name ->
                        !name.startsWith(".") &&
                            (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp"))
                    } == true
                    )
            }.sortedBy { it.name }

            if (files.isEmpty()) {
                throw Exception("No image files found in downloaded chapter")
            }

            translation.totalPages = files.size
            translation.completedPages = 0

            val translationsDir = chapterDir.createDirectory("translations")
                ?: throw Exception("Failed to create translations directory")

            val fromLang = preferences.translateFrom.get()
            val toLang = preferences.translateTo.get()

            for (index in files.indices) {
                val file = files[index]
                if (translation.state != ChapterTranslation.State.TRANSLATING) break

                translation.stage = "Processing page ${index + 1}/${files.size}"
                translation.progress = (index.toFloat() / files.size)

                val bitmap = file.openInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: continue

                // 1. OCR / Text detection
                translation.stage = "Detecting & OCR text on page ${index + 1}"
                val regions = ocrEngine.recognizeText(bitmap, fromLang)

                // 2. Translation
                translation.stage = "Translating page ${index + 1}"
                val translatedTexts = mutableListOf<String>()
                for (region in regions) {
                    val sample = region.sampleText
                    val translated = if (sample.isNotBlank()) {
                        translatorEngine.translateText(sample, fromLang, toLang)
                    } else {
                        ""
                    }
                    translatedTexts.add(translated)
                }

                // 3. Inpainting / Cleaning
                translation.stage = "Cleaning page ${index + 1}"
                val cleanedBitmap = textCleaner.cleanTextRegions(bitmap, regions)

                // 4. Canvas Redraw
                translation.stage = "Rendering page ${index + 1}"
                val finalBitmap = canvasRenderer.renderTranslations(cleanedBitmap, regions, translatedTexts)

                // 5. Save output
                val outFile = translationsDir.createFile(file.name ?: "page_$index.png")
                    ?: continue
                outFile.openOutputStream().use { outStream ->
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outStream)
                }

                translation.completedPages++
                translation.progress = (translation.completedPages.toFloat() / files.size)
                updateQueue()
            }

            if (translation.completedPages == files.size) {
                translation.state = ChapterTranslation.State.TRANSLATED
                translation.stage = "Completed"
            } else if (translation.state == ChapterTranslation.State.TRANSLATING) {
                translation.state = ChapterTranslation.State.ERROR
                translation.logs.add("Incomplete translation process")
            }
        } catch (e: Exception) {
            translation.state = ChapterTranslation.State.ERROR
            translation.logs.add(e.localizedMessage ?: "Unknown translation error")
        } finally {
            updateQueue()
        }
    }

    private fun updateQueue() {
        _queueState.value = translationsMap.values.toList()
    }
}
