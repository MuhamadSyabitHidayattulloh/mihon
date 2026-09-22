package eu.kanade.tachiyomi.data.translation

enum class TranslationStatus {
    NOT_TRANSLATED,
    QUEUE,
    TRANSLATING,
    TRANSLATED,
    ERROR,
}

data class TranslationProgressState(
    val chapterId: Long,
    val status: TranslationStatus = TranslationStatus.NOT_TRANSLATED,
    val currentStage: String = "",
    val doneCount: Int = 0,
    val queueCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0,
    val errorLogs: List<String> = emptyList(),
)
