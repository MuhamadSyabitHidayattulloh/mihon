package eu.kanade.tachiyomi.data.translation.model

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source

data class TranslationTask(
    val source: Source,
    val manga: Manga,
    val chapter: Chapter,
    var state: State = State.NOT_TRANSLATED,
    var progress: Float = 0f,
    var error: String? = null,
) {
    enum class State {
        NOT_TRANSLATED,
        QUEUE,
        TRANSLATING,
        TRANSLATED,
        ERROR,
    }
}
