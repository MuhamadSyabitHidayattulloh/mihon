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
    private val translationManager: eu.kanade.domain.translation.service.TranslationManager by injectLazy()
    private val readerPreferences: eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences by injectLazy()

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
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        val domainChapter = dbChapter.toDomainChapter()!!
        val isTranslatedAvailable = translationManager.isChapterTranslated(manga, domainChapter)
        val showTranslated = readerPreferences.showTranslated.get() && isTranslatedAvailable

        val pages = downloadManager.buildPageList(source, manga, domainChapter)
        return pages.map { page ->
            ReaderPage(page.index, page.url, page.imageUrl) {
                var streamUri = page.uri ?: Uri.EMPTY
                if (showTranslated && chapterPath != null) {
                    val pageName = page.uri?.lastPathSegment ?: "page_${page.number}.jpg"
                    val transFile = translationManager.getTranslatedPageFile(chapterPath, pageName)
                    if (transFile != null && transFile.exists()) {
                        streamUri = transFile.uri
                    }
                }
                context.contentResolver.openInputStream(streamUri)!!
            }.apply {
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
