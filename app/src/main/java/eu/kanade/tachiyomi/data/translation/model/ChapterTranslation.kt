package eu.kanade.tachiyomi.data.translation.model

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.CopyOnWriteArrayList

class ChapterTranslation(
    val manga: Manga,
    val chapter: Chapter,
) {
    enum class State {
        NOT_TRANSLATED,
        QUEUE,
        TRANSLATING,
        TRANSLATED,
        ERROR,
    }

    @Volatile
    var state: State = State.NOT_TRANSLATED

    @Volatile
    var progress: Float = 0f

    @Volatile
    var stage: String = ""

    val logs: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var totalPages: Int = 0

    @Volatile
    var completedPages: Int = 0
}
