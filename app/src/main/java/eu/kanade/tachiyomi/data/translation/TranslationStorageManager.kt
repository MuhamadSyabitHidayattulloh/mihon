package eu.kanade.tachiyomi.data.translation

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

@Inject
@SingleIn(AppScope::class)
class TranslationStorageManager(
    private val context: Context,
) {
    fun getTranslationDir(manga: Manga, source: Source, provider: DownloadProvider, chapter: Chapter): UniFile? {
        val chapterDir = provider.findChapterDir(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            source = source,
        ) ?: return null

        return if (chapterDir.isFile && chapterDir.name?.endsWith(".cbz", ignoreCase = true) == true) {
            val parent = chapterDir.parentFile ?: return null
            parent.createDirectory("translations")?.createDirectory(chapter.id.toString())
        } else {
            chapterDir.createDirectory("translations")
        }
    }

    fun hasTranslation(manga: Manga, source: Source, provider: DownloadProvider, chapter: Chapter): Boolean {
        val dir = getTranslationDir(manga, source, provider, chapter) ?: return false
        val files = dir.listFiles()
        return !files.isNullOrEmpty()
    }

    fun deleteTranslation(manga: Manga, source: Source, provider: DownloadProvider, chapter: Chapter): Boolean {
        val dir = getTranslationDir(manga, source, provider, chapter)
        return dir?.delete() ?: false
    }
}
