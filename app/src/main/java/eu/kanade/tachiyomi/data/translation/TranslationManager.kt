package eu.kanade.tachiyomi.data.translation

import android.graphics.BitmapFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngine
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.model.TranslationProgress
import tachiyomi.domain.translation.model.TranslationState
import java.io.BufferedOutputStream

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val translationStorageManager: TranslationStorageManager,
    private val translationEngine: TranslationEngine,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _progressMap = MutableStateFlow<Map<Long, TranslationProgress>>(emptyMap())
    val progressMap: StateFlow<Map<Long, TranslationProgress>> = _progressMap.asStateFlow()

    fun getProgress(chapterId: Long): TranslationProgress {
        return _progressMap.value[chapterId] ?: TranslationProgress(
            chapterId = chapterId,
            state = TranslationState.NOT_TRANSLATED,
        )
    }

    fun isTranslated(manga: Manga, source: Source, chapter: Chapter): Boolean {
        return translationStorageManager.hasTranslation(manga, source, downloadManager.provider, chapter)
    }

    fun translateChapter(manga: Manga, chapter: Chapter) {
        scope.launch {
            val source = sourceManager.getOrStub(manga.source)
            val chapterDir = downloadManager.provider.findChapterDir(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                chapterUrl = chapter.url,
                mangaTitle = manga.title,
                source = source,
            )

            if (chapterDir == null) {
                updateProgress(chapter.id) {
                    it.copy(
                        state = TranslationState.ERROR,
                        lastError = "Chapter directory not found",
                        logs = it.logs + "Gagal: Folder chapter tidak ditemukan",
                    )
                }
                return@launch
            }

            val targetDir = translationStorageManager.getTranslationDir(
                manga,
                source,
                downloadManager.provider,
                chapter,
            )
            if (targetDir == null) {
                updateProgress(chapter.id) {
                    it.copy(
                        state = TranslationState.ERROR,
                        lastError = "Failed to create translation directory",
                        logs = it.logs + "Gagal: Folder terjemahan tidak dapat dibuat",
                    )
                }
                return@launch
            }

            val imageFiles = chapterDir.listFiles()?.filter { file ->
                val name = file.name?.lowercase() ?: ""
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
            }?.sortedBy { it.name } ?: emptyList()

            val total = imageFiles.size
            updateProgress(chapter.id) {
                TranslationProgress(
                    chapterId = chapter.id,
                    state = TranslationState.TRANSLATING,
                    totalPages = total,
                    progressPagesDone = 0,
                    logs = listOf("Memulai terjemahan chapter (Total $total halaman)..."),
                )
            }

            var success = 0
            var failed = 0

            for ((index, file) in imageFiles.withIndex()) {
                val filename = file.name ?: "page_$index.jpg"
                try {
                    val bitmap = file.openInputStream().use { BitmapFactory.decodeStream(it) }
                    if (bitmap != null) {
                        val translatedBitmap = translationEngine.translateImage(bitmap) { stepStr, log ->
                            updateProgress(chapter.id) { p ->
                                val stepEnum = try {
                                    TranslationProgress.Step.valueOf(stepStr)
                                } catch (e: Exception) {
                                    TranslationProgress.Step.TRANSLATION
                                }
                                p.copy(currentStep = stepEnum, logs = p.logs + log)
                            }
                        }

                        val outFile = targetDir.createFile(filename)
                        outFile?.openOutputStream()?.use { out ->
                            BufferedOutputStream(out).use { bos ->
                                translatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, bos)
                            }
                        }
                        success++
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                    updateProgress(chapter.id) { p ->
                        p.copy(logs = p.logs + "Gagal menerjemahkan $filename: ${e.message}")
                    }
                }

                updateProgress(chapter.id) { p ->
                    p.copy(
                        progressPagesDone = index + 1,
                        successCount = success,
                        failedCount = failed,
                    )
                }
            }

            updateProgress(chapter.id) { p ->
                p.copy(
                    state = if (failed == 0) TranslationState.TRANSLATED else TranslationState.ERROR,
                    currentStep = TranslationProgress.Step.FINISHED,
                    logs = p.logs + "Selesai: $success berhasil, $failed gagal.",
                )
            }
        }
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter) {
        scope.launch {
            val source = sourceManager.getOrStub(manga.source)
            translationStorageManager.deleteTranslation(manga, source, downloadManager.provider, chapter)
            _progressMap.value = _progressMap.value.toMutableMap().apply {
                remove(chapter.id)
            }
        }
    }

    private fun updateProgress(chapterId: Long, block: (TranslationProgress) -> TranslationProgress) {
        val current = getProgress(chapterId)
        val updated = block(current)
        _progressMap.value = _progressMap.value.toMutableMap().apply {
            put(chapterId, updated)
        }
    }
}
