package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Inject
@SingleIn(AppScope::class)
class TranslationStorage(
    private val context: Context,
    private val storageManager: StorageManager,
    private val downloadProvider: DownloadProvider,
    private val downloadPreferences: DownloadPreferences,
) {

    fun getTranslationsDir(): UniFile? {
        return storageManager.getTranslationsDirectory()
    }

    fun getSourceDir(source: Source): UniFile? {
        val base = getTranslationsDir() ?: return null
        val sourceDirName = downloadProvider.getProviderDirName(source)
        return base.findFile(sourceDirName) ?: base.createDirectory(sourceDirName)
    }

    fun getMangaDir(manga: Manga, source: Source): UniFile? {
        val sourceDir = getSourceDir(source) ?: return null
        val mangaDirName = downloadProvider.getMangaDirName(manga.title)
        return sourceDir.findFile(mangaDirName) ?: sourceDir.createDirectory(mangaDirName)
    }

    fun getChapterFile(chapter: Chapter, manga: Manga, source: Source): UniFile? {
        val mangaDir = getMangaDir(manga, source) ?: return null
        val chapterDirName = downloadProvider.getChapterDirName(chapter.name, chapter.scanlator)
        val isCbz = downloadPreferences.saveChaptersAsCBZ().get()

        val fileName = if (isCbz) "$chapterDirName.cbz" else chapterDirName
        return mangaDir.findFile(fileName)
    }

    fun isChapterTranslated(chapter: Chapter, manga: Manga, source: Source): Boolean {
        val file = getChapterFile(chapter, manga, source) ?: return false
        return file.exists()
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source): Boolean {
        val file = getChapterFile(chapter, manga, source) ?: return false
        return file.delete()
    }

    fun saveTranslatedPages(
        chapter: Chapter,
        manga: Manga,
        source: Source,
        translatedBitmaps: List<Bitmap>,
    ): Boolean {
        val mangaDir = getMangaDir(manga, source) ?: return false
        val chapterName = downloadProvider.getChapterDirName(chapter.name, chapter.scanlator)
        val saveAsCbz = downloadPreferences.saveChaptersAsCBZ().get()

        if (saveAsCbz) {
            val cbzFile = mangaDir.createFile("$chapterName.cbz") ?: return false
            try {
                val outputStream = cbzFile.openOutputStream()
                ZipOutputStream(outputStream).use { zipOut ->
                    for ((index, bitmap) in translatedBitmaps.withIndex()) {
                        val entryName = String.format("%03d.jpg", index + 1)
                        zipOut.putNextEntry(ZipEntry(entryName))
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zipOut)
                        zipOut.closeEntry()
                    }
                }
                return true
            } catch (e: Exception) {
                cbzFile.delete()
                return false
            }
        } else {
            val chapterDir = mangaDir.createDirectory(chapterName) ?: return false
            for ((index, bitmap) in translatedBitmaps.withIndex()) {
                val fileName = String.format("%03d.jpg", index + 1)
                val pageFile = chapterDir.createFile(fileName) ?: continue
                try {
                    pageFile.openOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return true
        }
    }

    fun getTranslatedPageInputStream(chapter: Chapter, manga: Manga, source: Source, pageIndex: Int): InputStream? {
        val file = getChapterFile(chapter, manga, source) ?: return null
        if (!file.exists()) return null

        val fileName = String.format("%03d.jpg", pageIndex + 1)

        return if (file.name?.endsWith(".cbz", ignoreCase = true) == true) {
            try {
                val tempZipFile = File(context.cacheDir, file.name!!)
                file.openInputStream().use { input ->
                    FileOutputStream(tempZipFile).use { output -> input.copyTo(output) }
                }
                val bytes = ZipFile(tempZipFile).use { zip ->
                    val entry = zip.getEntry(fileName) ?: zip.getEntry(String.format("%03d.png", pageIndex + 1))
                    entry?.let { zip.getInputStream(it).readBytes() }
                }
                tempZipFile.delete()
                bytes?.let { java.io.ByteArrayInputStream(it) }
            } catch (e: Exception) {
                null
            }
        } else {
            val pageFile = file.findFile(fileName) ?: file.findFile(String.format("%03d.png", pageIndex + 1))
            pageFile?.openInputStream()
        }
    }
}
