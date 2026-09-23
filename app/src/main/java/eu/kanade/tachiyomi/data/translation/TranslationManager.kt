package eu.kanade.tachiyomi.data.translation

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.translation.model.TranslationQueueItem
import tachiyomi.domain.translation.model.TranslationStatus
import tachiyomi.domain.translation.service.TranslationPreferences

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val storageManager: StorageManager,
    private val downloadProvider: DownloadProvider,
    private val sourceManager: SourceManager,
    private val pipelineManager: TranslationPipelineManager,
    private val preferences: TranslationPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _queue = MutableStateFlow<List<TranslationQueueItem>>(emptyList())
    val queue: StateFlow<List<TranslationQueueItem>> = _queue.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun isChapterTranslated(manga: Manga, chapter: Chapter): Boolean {
        val chapterDir = getTranslationChapterDir(manga, chapter) ?: return false
        return chapterDir.exists() && (chapterDir.listFiles()?.isNotEmpty() == true)
    }

    fun getTranslationChapterDir(manga: Manga, chapter: Chapter): UniFile? {
        val source = sourceManager.getOrStub(manga.source)
        val translationsDir = storageManager.getTranslationsDirectory() ?: return false
        val sourceDir = translationsDir.createDirectory(downloadProvider.getProviderDirName(source)) ?: return false
        val mangaDir = sourceDir.createDirectory(downloadProvider.getMangaDirName(manga.title)) ?: return false
        return mangaDir.createDirectory(downloadProvider.getChapterDirName(chapter.name, chapter.scanlator))
    }

    fun enqueueChapter(manga: Manga, chapter: Chapter) {
        if (queue.value.any { it.chapterId == chapter.id }) return

        val item = TranslationQueueItem(
            mangaId = manga.id,
            chapterId = chapter.id,
            mangaTitle = manga.title,
            chapterName = chapter.name,
            chapterNumber = chapter.chapterNumber,
            status = TranslationStatus.QUEUED,
        )

        _queue.value = _queue.value + item
        startProcessingIfNeeded(manga, chapter)
    }

    fun deleteTranslation(manga: Manga, chapter: Chapter) {
        val chapterDir = getTranslationChapterDir(manga, chapter)
        chapterDir?.delete()
        _queue.value = _queue.value.filterNot { it.chapterId == chapter.id }
    }

    fun pauseQueue() {
        _isProcessing.value = false
    }

    fun resumeQueue(mangaMap: Map<Long, Manga>, chapterMap: Map<Long, Chapter>) {
        if (!_isProcessing.value && _queue.value.isNotEmpty()) {
            _isProcessing.value = true
            scope.launch {
                processNextInQueue(mangaMap, chapterMap)
            }
        }
    }

    fun cancelQueueItem(chapterId: Long) {
        _queue.value = _queue.value.filterNot { it.chapterId == chapterId }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _queue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _queue.value = currentList
        }
    }

    private fun startProcessingIfNeeded(manga: Manga, chapter: Chapter) {
        if (!_isProcessing.value) {
            _isProcessing.value = true
            scope.launch {
                processNextInQueue(mapOf(manga.id to manga), mapOf(chapter.id to chapter))
            }
        }
    }

    private suspend fun processNextInQueue(
        mangaMap: Map<Long, Manga>,
        chapterMap: Map<Long, Chapter>,
    ) {
        while (_isProcessing.value) {
            val nextItem = _queue.value.firstOrNull { it.status == TranslationStatus.QUEUED } ?: break
            val manga = mangaMap[nextItem.mangaId]
            val chapter = chapterMap[nextItem.chapterId]

            if (manga == null || chapter == null) {
                updateItemStatus(nextItem.chapterId, TranslationStatus.ERROR, error = "Manga or Chapter not found")
                continue
            }

            updateItemStatus(nextItem.chapterId, TranslationStatus.TRANSLATING)

            val downloadDir = downloadProvider.findChapterDir(
                chapter.name,
                chapter.scanlator,
                manga.title,
                sourceManager.getOrStub(manga.source),
            )
            if (downloadDir == null || !downloadDir.exists()) {
                updateItemStatus(
                    chapterId = nextItem.chapterId,
                    status = TranslationStatus.ERROR,
                    error = "Downloaded files not found",
                )
                continue
            }

            val targetDir = getTranslationChapterDir(manga, chapter)
            if (targetDir == null) {
                updateItemStatus(
                    nextItem.chapterId,
                    TranslationStatus.ERROR,
                    error = "Failed to create translation directory",
                )
                continue
            }

            val files = downloadDir.listFiles()?.filter { DiskUtil.isImage(it.name ?: "") } ?: emptyList()
            if (files.isEmpty()) {
                updateItemStatus(nextItem.chapterId, TranslationStatus.ERROR, error = "No images found in download")
                continue
            }

            var successCount = 0
            val total = files.size

            for ((index, file) in files.withIndex()) {
                if (!_isProcessing.value) break
                try {
                    val bytes = file.openInputStream().use { it.readBytes() }
                    val translatedBytes = pipelineManager.processImage(bytes)

                    val outFile = targetDir.createFile(file.name ?: "page_$index.jpg")
                    outFile?.openOutputStream()?.use { it.write(translatedBytes) }

                    successCount++
                    val progress = (index + 1).toFloat() / total
                    updateItemProgress(nextItem.chapterId, progress, total, successCount)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (successCount == total) {
                updateItemStatus(nextItem.chapterId, TranslationStatus.TRANSLATED, progress = 1f)
            } else {
                updateItemStatus(nextItem.chapterId, TranslationStatus.ERROR, error = "Completed with errors")
            }
        }
        _isProcessing.value = false
    }

    private fun updateItemStatus(
        chapterId: Long,
        status: TranslationStatus,
        progress: Float = 0f,
        error: String? = null,
    ) {
        _queue.value = _queue.value.map {
            if (it.chapterId == chapterId) {
                it.copy(status = status, progress = progress, error = error)
            } else {
                it
            }
        }
    }

    private fun updateItemProgress(chapterId: Long, progress: Float, totalPages: Int, translatedPages: Int) {
        _queue.value = _queue.value.map {
            if (it.chapterId == chapterId) {
                it.copy(progress = progress, totalPages = totalPages, translatedPages = translatedPages)
            } else {
                it
            }
        }
    }
}
