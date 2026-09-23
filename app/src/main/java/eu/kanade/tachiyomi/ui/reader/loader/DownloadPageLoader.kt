package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.archiveReader
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
) : PageLoader() {

    private val context: Context by injectLazy()

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val pages = downloadManager.buildPageList(source, manga, chapter.chapter.toDomainChapter()!!)
        val readerPreferences: eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences by injectLazy()
        val storageManager: tachiyomi.domain.storage.service.StorageManager by injectLazy()
        val isTranslationActive = readerPreferences.showTranslationMode.get()
        val translationsDir = storageManager.getTranslationsDirectory()
        val sourceDir = translationsDir?.findFile(downloadProvider.getSourceDirName(source))
        val mangaDir = sourceDir?.findFile(downloadProvider.getMangaDirName(manga.title))
        val transChapterDir = mangaDir?.findFile(
            downloadProvider.getChapterDirName(chapter.chapter.name, chapter.chapter.scanlator, chapter.chapter.url),
        )

        return pages.map { page ->
            ReaderPage(page.index, page.url, page.imageUrl) {
                val origUri = page.uri ?: Uri.EMPTY
                val pageFileName = origUri.lastPathSegment
                val transFile = if (isTranslationActive && pageFileName != null && transChapterDir?.exists() == true) {
                    transChapterDir.findFile(pageFileName)
                } else {
                    null
                }

                if (transFile != null && transFile.exists()) {
                    transFile.openInputStream()
                } else {
                    context.contentResolver.openInputStream(origUri)!!
                }
            }.apply {
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
