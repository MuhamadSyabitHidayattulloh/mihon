package eu.kanade.domain.translation.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.translation.engine.GeminiTranslationEngine
import eu.kanade.domain.translation.engine.GoogleWebTranslationEngine
import eu.kanade.domain.translation.engine.MlKitTranslationEngine
import eu.kanade.domain.translation.engine.OpenRouterTranslationEngine
import eu.kanade.domain.translation.engine.TranslationEngine
import eu.kanade.domain.translation.model.ChapterTranslationProgress
import eu.kanade.domain.translation.model.TranslationQueueStats
import eu.kanade.domain.translation.model.TranslationStage
import eu.kanade.domain.translation.model.TranslationState
import eu.kanade.domain.translation.onnx.OnnxTranslationPipeline
import eu.kanade.domain.translation.onnx.TranslationModelDownloader
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val preferences: TranslationPreferences,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val networkHelper: NetworkHelper,
    private val pipeline: OnnxTranslationPipeline,
    private val modelDownloader: TranslationModelDownloader,
    private val json: Json,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val progressMap = ConcurrentHashMap<Long, ChapterTranslationProgress>()
    private val _progressFlow = MutableStateFlow<Map<Long, ChapterTranslationProgress>>(emptyMap())
    val progressFlow: StateFlow<Map<Long, ChapterTranslationProgress>> = _progressFlow.asStateFlow()

    fun getChapterProgress(chapterId: Long): ChapterTranslationProgress {
        return progressMap[chapterId] ?: ChapterTranslationProgress(chapterId = chapterId, mangaId = 0)
    }

    fun getQueueStats(): TranslationQueueStats {
        val values = progressMap.values
        val done = values.count { it.state == TranslationState.TRANSLATED }
        val queue = values.count { it.state == TranslationState.QUEUED || it.state == TranslationState.RUNNING }
        val failed = values.count { it.state == TranslationState.ERROR }
        return TranslationQueueStats(doneCount = done, queueCount = queue, failedCount = failed)
    }

    fun isChapterTranslated(manga: Manga, chapter: Chapter): Boolean {
        val source = runBlocking { sourceManager.get(manga.source) } ?: return false
        val chapterDir = downloadProvider.findChapterDir(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            source = source,
        ) ?: return false

        val translationsDir = getTranslationsDir(chapterDir, manga, source)
        return translationsDir != null && translationsDir.exists() &&
            (translationsDir.listFiles()?.isNotEmpty() == true)
    }

    fun getTranslationsDir(chapterDir: UniFile, manga: Manga, source: Source): UniFile? {
        if (chapterDir.isDirectory) {
            val trans = chapterDir.findFile("translations")
            if (trans != null && trans.exists()) return trans
            return chapterDir.createDirectory("translations")
        } else if (chapterDir.name?.endsWith(".cbz", ignoreCase = true) == true) {
            val mangaDir = downloadProvider.findMangaDir(manga.title, source) ?: return null
            val folderName = (chapterDir.name ?: "chapter").removeSuffix(".cbz") + "_translations"
            val trans = mangaDir.findFile(folderName)
            if (trans != null && trans.exists()) return trans
            return mangaDir.createDirectory(folderName)
        }
        return null
    }

    fun getTranslatedPageFile(chapterDir: UniFile, pageName: String): UniFile? {
        val transDir = chapterDir.findFile("translations")
            ?: chapterDir.findFile("${chapterDir.name?.removeSuffix(".cbz")}_translations")
            ?: return null
        return transDir.findFile(pageName) ?: transDir.findFile(pageName.replace(Regex("\\.[^.]+$"), ".jpg"))
    }

    fun translateChapter(manga: Manga, chapter: Chapter) {
        val chapterId = chapter.id
        val currentProgress = progressMap[chapterId]
        if (currentProgress?.state == TranslationState.RUNNING || currentProgress?.state == TranslationState.QUEUED) {
            return
        }

        val initial = ChapterTranslationProgress(
            chapterId = chapterId,
            mangaId = manga.id,
            state = TranslationState.QUEUED,
            stage = TranslationStage.IDLE,
            logs = listOf("Chapter queued for translation."),
        )
        updateProgress(chapterId, initial)

        scope.launch {
            processChapterTranslation(manga, chapter)
        }
    }

    private suspend fun processChapterTranslation(manga: Manga, chapter: Chapter) {
        val chapterId = chapter.id
        val notificationId = (Notifications.ID_TRANSLATION_CHAPTER_PROGRESS - chapterId.toInt()).toInt()
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
            .setContentTitle("Translating Chapter ${chapter.name}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        try {
            updateProgress(chapterId) {
                it.copy(
                    state = TranslationState.RUNNING,
                    stage = TranslationStage.DETECTION,
                    logs = it.logs + "Starting translation process...",
                )
            }
            context.notificationManager.notify(notificationId, builder.build())

            if (!modelDownloader.isAllModelsDownloaded()) {
                updateProgress(chapterId) {
                    it.copy(logs = it.logs + "Downloading required ONNX models...")
                }
                modelDownloader.downloadAllModels()
            }

            val source = sourceManager.get(manga.source)
                ?: throw IllegalStateException("Source not found for manga")

            val chapterDir = downloadProvider.findChapterDir(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                chapterUrl = chapter.url,
                mangaTitle = manga.title,
                source = source,
            ) ?: throw IllegalStateException("Chapter directory not found. Please download chapter first.")

            val transDir = getTranslationsDir(chapterDir, manga, source)
                ?: throw IllegalStateException("Could not create translations directory")

            val pageFiles = getPageFiles(chapterDir)
            if (pageFiles.isEmpty()) {
                throw IllegalStateException("No page files found in chapter directory")
            }

            updateProgress(chapterId) {
                it.copy(
                    totalPages = pageFiles.size,
                    logs = it.logs + "Found ${pageFiles.size} pages to translate.",
                )
            }

            val activeEngine = createTranslationEngine()
            val fromLang = preferences.translateFrom.get()
            val toLang = preferences.translateTo.get()

            for ((index, pageFile) in pageFiles.withIndex()) {
                val pageNum = index + 1
                val pageName = pageFile.name ?: "page_$pageNum.jpg"

                updateProgress(chapterId) {
                    it.copy(
                        currentPage = pageNum,
                        logs = it.logs + "Processing Page $pageNum/${pageFiles.size} ($pageName)",
                    )
                }

                builder.setContentText("Translating Page $pageNum/${pageFiles.size}")
                    .setProgress(pageFiles.size, pageNum, false)
                context.notificationManager.notify(notificationId, builder.build())

                val bitmap = loadBitmapFromUniFile(pageFile)
                if (bitmap != null) {
                    val translatedBitmap = pipeline.processPage(
                        originalBitmap = bitmap,
                        engine = activeEngine,
                        fromLang = fromLang,
                        toLang = toLang,
                        onStageChanged = { stageName ->
                            val stage = when (stageName) {
                                "DETECTION" -> TranslationStage.DETECTION
                                "OCR" -> TranslationStage.OCR
                                "TRANSLATION" -> TranslationStage.TRANSLATION
                                "CLEANING" -> TranslationStage.CLEANING
                                "RENDERING" -> TranslationStage.RENDERING
                                else -> TranslationStage.IDLE
                            }
                            updateProgress(chapterId) { p -> p.copy(stage = stage) }
                        },
                    )

                    saveBitmapToUniFile(translatedBitmap, transDir, pageName)
                    translatedBitmap.recycle()
                    bitmap.recycle()
                }
            }

            updateProgress(chapterId) {
                it.copy(
                    state = TranslationState.TRANSLATED,
                    stage = TranslationStage.IDLE,
                    logs = it.logs + "Translation completed successfully!",
                )
            }

            builder.setContentTitle("Translation Complete")
                .setContentText("Chapter ${chapter.name} translated")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setProgress(0, 0, false)
            context.notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error translating chapter ${chapter.name}" }
            updateProgress(chapterId) {
                it.copy(
                    state = TranslationState.ERROR,
                    stage = TranslationStage.IDLE,
                    errorMessage = e.localizedMessage,
                    logs = it.logs + "Error: ${e.localizedMessage}",
                )
            }

            builder.setContentTitle("Translation Failed")
                .setContentText("Error: ${e.localizedMessage}")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false)
                .setProgress(0, 0, false)
            context.notificationManager.notify(notificationId, builder.build())
        }
    }

    private fun createTranslationEngine(): TranslationEngine {
        return when (preferences.translatorType.get()) {
            TranslationPreferences.TRANSLATOR_GOOGLE -> GoogleWebTranslationEngine(networkHelper, json)
            TranslationPreferences.TRANSLATOR_GEMINI -> GeminiTranslationEngine(networkHelper, preferences, json)
            TranslationPreferences.TRANSLATOR_OPENROUTER -> OpenRouterTranslationEngine(
                networkHelper,
                preferences,
                json,
            )
            else -> MlKitTranslationEngine()
        }
    }

    private fun getPageFiles(chapterDir: UniFile): List<UniFile> {
        val files = chapterDir.listFiles() ?: return emptyList()
        return files.filter { file ->
            val name = file.name?.lowercase() ?: ""
            !file.isDirectory && (
                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".webp")
                )
        }.sortedBy { it.name }
    }

    private fun loadBitmapFromUniFile(file: UniFile): Bitmap? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToUniFile(bitmap: Bitmap, transDir: UniFile, originalName: String) {
        val fileName = if (originalName.contains(".")) originalName else "$originalName.jpg"
        val targetFile = transDir.findFile(fileName) ?: transDir.createFile(fileName) ?: return

        context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter) {
        val source = runBlocking { sourceManager.get(manga.source) } ?: return
        val chapterDir = downloadProvider.findChapterDir(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            source = source,
        ) ?: return

        val transDir = getTranslationsDir(chapterDir, manga, source)
        transDir?.delete()

        progressMap.remove(chapter.id)
        _progressFlow.value = progressMap.toMap()
    }

    private fun updateProgress(chapterId: Long, update: (ChapterTranslationProgress) -> ChapterTranslationProgress) {
        val current = getChapterProgress(chapterId)
        val updated = update(current)
        progressMap[chapterId] = updated
        _progressFlow.value = progressMap.toMap()
    }

    private fun updateProgress(chapterId: Long, progress: ChapterTranslationProgress) {
        progressMap[chapterId] = progress
        _progressFlow.value = progressMap.toMap()
    }
}
