package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
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
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val translationManager: TranslationManager by injectLazy()

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
        val pages = loader.getPages()
        val showTranslation = readerPreferences.showTranslation.get()
        val dbChapter = chapter.chapter.toDomainChapter()!!
        if (showTranslation) {
            pages.forEachIndexed { index, page ->
                val fileName = "${index + 1}.jpg"
                val translatedFile = kotlinx.coroutines.runBlocking {
                    translationManager.getTranslatedPageFile(manga, dbChapter, fileName)
                }
                if (translatedFile != null && translatedFile.exists()) {
                    page.stream = { translatedFile.openInputStream() }
                }
            }
        }
        return pages
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val pages = downloadManager.buildPageList(source, manga, chapter.chapter.toDomainChapter()!!)
        val showTranslation = readerPreferences.showTranslation.get()
        val dbChapter = chapter.chapter.toDomainChapter()!!

        return pages.map { page ->
            val originalStreamFn = { context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)!! }
            val streamFn = if (showTranslation) {
                val fileName = page.uri?.lastPathSegment ?: "${page.index + 1}.jpg"
                val translatedFile = kotlinx.coroutines.runBlocking {
                    translationManager.getTranslatedPageFile(manga, dbChapter, fileName)
                }
                if (translatedFile != null && translatedFile.exists()) {
                    { translatedFile.openInputStream() }
                } else {
                    originalStreamFn
                }
            } else {
                originalStreamFn
            }

            ReaderPage(page.index, page.url, page.imageUrl, streamFn).apply {
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
