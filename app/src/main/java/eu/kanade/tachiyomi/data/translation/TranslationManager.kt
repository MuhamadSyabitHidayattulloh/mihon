package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.engine.TranslationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.model.TranslationProgress
import tachiyomi.domain.translation.model.TranslationStage
import tachiyomi.domain.translation.model.TranslationState
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val downloadProvider: DownloadProvider,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
    private val modelManager: TranslationModelManager,
    private val preferences: TranslationPreferences,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val pipeline by lazy { TranslationPipeline(context, modelManager, preferences, okHttpClient) }

    private val _progresses = MutableStateFlow<Map<Long, TranslationProgress>>(emptyMap())
    val progresses: StateFlow<Map<Long, TranslationProgress>> = _progresses.asStateFlow()

    fun getProgress(chapterId: Long): TranslationProgress {
        return _progresses.value[chapterId] ?: TranslationProgress(chapterId)
    }

    fun isChapterTranslated(manga: Manga, chapter: Chapter): Boolean {
        val source = sourceManager.get(manga.source) ?: return false
        val chapterDir =
            downloadProvider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
                ?: return false

        val translationsDir = if (chapterDir.name?.endsWith(".cbz") == true) {
            val parent = chapterDir.parent
            parent?.findFile("${chapterDir.name}_translations")
        } else {
            chapterDir.findFile("translations")
        }

        return translationsDir != null && translationsDir.exists() &&
            (translationsDir.listFiles()?.isNotEmpty() == true)
    }

    fun getTranslatedImageFile(manga: Manga, chapter: Chapter, pageName: String): UniFile? {
        val source = sourceManager.get(manga.source) ?: return null
        val chapterDir =
            downloadProvider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
                ?: return null

        val translationsDir = if (chapterDir.name?.endsWith(".cbz") == true) {
            val parent = chapterDir.parent
            parent?.findFile("${chapterDir.name}_translations")
        } else {
            chapterDir.findFile("translations")
        }

        return translationsDir?.findFile(pageName)
    }

    fun translateChapter(manga: Manga, chapter: Chapter) {
        scope.launch {
            updateProgress(chapter.id) {
                it.copy(
                    state = TranslationState.QUEUED,
                    stage = TranslationStage.IDLE,
                    logs = it.logs + "Added chapter ${chapter.name} to translation queue.",
                )
            }

            try {
                processChapterTranslation(manga, chapter)
            } catch (e: Exception) {
                updateProgress(chapter.id) {
                    it.copy(
                        state = TranslationState.ERROR,
                        errorMessage = e.localizedMessage ?: "Translation failed",
                        logs = it.logs + "ERROR: ${e.localizedMessage}",
                    )
                }
            }
        }
    }

    private suspend fun processChapterTranslation(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source)
            ?: throw IllegalStateException("Source not found")
        val chapterDir =
            downloadProvider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
                ?: throw IllegalStateException("Downloaded chapter not found")

        val targetDir: UniFile = if (chapterDir.name?.endsWith(".cbz") == true) {
            val parent = chapterDir.parent ?: throw IllegalStateException("Parent dir null")
            val folderName = "${chapterDir.name}_translations"
            parent.findFile(folderName) ?: parent.createDirectory(folderName)
                ?: throw IllegalStateException("Could not create translations directory")
        } else {
            chapterDir.findFile("translations") ?: chapterDir.createDirectory("translations")
                ?: throw IllegalStateException("Could not create translations directory inside chapter folder")
        }

        updateProgress(chapter.id) {
            it.copy(
                state = TranslationState.TRANSLATING,
                stage = TranslationStage.DETECTION,
                logs = it.logs + "Starting translation processing...",
            )
        }

        val pages = getChapterPages(chapterDir)
        val total = pages.size

        updateProgress(chapter.id) {
            it.copy(totalPages = total)
        }

        for ((index, page) in pages.withIndex()) {
            val pageName = page.first
            val originalBitmap = page.second ?: continue

            updateProgress(chapter.id) {
                it.copy(
                    progressPages = index + 1,
                    stage = TranslationStage.DETECTION,
                    logs = it.logs + "Processing page ${index + 1}/$total ($pageName)",
                )
            }

            val translatedBitmap = pipeline.processImage(originalBitmap) { logMsg ->
                val stage = when {
                    logMsg.contains("detection", true) -> TranslationStage.DETECTION
                    logMsg.contains("OCR", true) -> TranslationStage.OCR
                    logMsg.contains("Cleaning", true) -> TranslationStage.CLEANING
                    logMsg.contains("Translating", true) -> TranslationStage.TRANSLATION
                    logMsg.contains("Rendering", true) -> TranslationStage.CANVAS_RENDER
                    else -> TranslationStage.TRANSLATION
                }
                updateProgress(chapter.id) { curr ->
                    curr.copy(
                        stage = stage,
                        logs = curr.logs + logMsg,
                    )
                }
            }

            // Save translated page
            val targetFile = targetDir.findFile(pageName) ?: targetDir.createFile(pageName)
            targetFile?.openOutputStream()?.use { out ->
                translatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }

        updateProgress(chapter.id) {
            it.copy(
                state = TranslationState.TRANSLATED,
                stage = TranslationStage.IDLE,
                logs = it.logs + "Chapter translation completed successfully!",
            )
        }
    }

    private fun getChapterPages(chapterDir: UniFile): List<Pair<String, Bitmap?>> {
        val pages = mutableListOf<Pair<String, Bitmap?>>()

        if (chapterDir.name?.endsWith(".cbz") == true) {
            val file = File(chapterDir.filePath)
            if (file.exists()) {
                ZipFile(file).use { zip ->
                    val entries = zip.entries().asSequence().toList().filter { !it.isDirectory }
                    for (entry in entries) {
                        val inputStream = zip.getInputStream(entry)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        pages.add(entry.name to bitmap)
                    }
                }
            }
        } else {
            val files = chapterDir.listFiles()?.filter { it.isFile && it.name != "translations" } ?: emptyList()
            for (f in files) {
                f.openInputStream().use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    pages.add((f.name ?: "page.jpg") to bitmap)
                }
            }
        }

        return pages
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) ?: return
        val chapterDir =
            downloadProvider.findChapterDir(chapter.name, chapter.scanlator, chapter.url, manga.title, source)
                ?: return

        val targetDir = if (chapterDir.name?.endsWith(".cbz") == true) {
            val parent = chapterDir.parent
            parent?.findFile("${chapterDir.name}_translations")
        } else {
            chapterDir.findFile("translations")
        }

        targetDir?.delete()

        _progresses.update { map ->
            map - chapter.id
        }
    }

    private fun updateProgress(chapterId: Long, block: (TranslationProgress) -> TranslationProgress) {
        _progresses.update { map ->
            val curr = map[chapterId] ?: TranslationProgress(chapterId)
            map + (chapterId to block(curr))
        }
    }
}
