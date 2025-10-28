
package eu.kanade.tachiyomi.data.translation.model

data class Translation(
    val chapterId: Long,
    val state: State,
) {
    enum class State {
        NOT_TRANSLATED,
        TRANSLATING,
        TRANSLATED,
    }
}
