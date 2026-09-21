package eu.kanade.tachiyomi.data.translation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class TranslationJob(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chapterId = inputData.getLong(KEY_CHAPTER_ID, -1L)
        val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)
        if (chapterId == -1L || mangaId == -1L) return Result.failure()

        val getChapter: GetChapter = Injekt.get()
        val getManga: GetManga = Injekt.get()
        val downloadProvider: eu.kanade.tachiyomi.data.download.DownloadProvider = Injekt.get()
        val sourceManager: SourceManager = Injekt.get()
        val translationManager: TranslationManager = Injekt.get()

        val chapter = getChapter.await(chapterId) ?: return Result.failure()
        val manga = getManga.await(mangaId) ?: return Result.failure()
        val source = sourceManager.get(manga.source) ?: return Result.failure()

        val chapterDirFile = downloadProvider.findChapterDir(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            source = source,
        ) ?: return Result.failure()

        val file = File(chapterDirFile.uri.path ?: return Result.failure())

        if (file.isDirectory) {
            translationManager.translateChapterDirectory(chapterId, file)
        } else if (file.isFile && file.name.endsWith(".cbz", ignoreCase = true)) {
            val parentDir = file.parentFile ?: return Result.failure()
            val tempDir = File(parentDir, file.nameWithoutExtension)
            if (!tempDir.exists()) tempDir.mkdirs()

            // Extract CBZ entries into tempDir
            java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (
                            entry.name.endsWith(".jpg", true) ||
                                entry.name.endsWith(".png", true) ||
                                entry.name.endsWith(".webp", true)
                            )
                    ) {
                        val outFile = File(tempDir, File(entry.name).name)
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                    entry = zip.nextEntry
                }
            }

            // Translate pages and place into tempDir/translations
            translationManager.translateChapterDirectory(chapterId, tempDir)
        }

        return Result.success()
    }

    companion object {
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_MANGA_ID = "manga_id"
    }
}
