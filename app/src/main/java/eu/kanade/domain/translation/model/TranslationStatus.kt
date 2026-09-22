package eu.kanade.domain.translation.model

enum class TranslationState {
    NOT_TRANSLATED,
    QUEUED,
    RUNNING,
    TRANSLATED,
    ERROR,
}

enum class TranslationStage {
    IDLE,
    DETECTION,
    OCR,
    TRANSLATION,
    CLEANING,
    RENDERING,
}

data class ChapterTranslationProgress(
    val chapterId: Long,
    val mangaId: Long,
    val state: TranslationState = TranslationState.NOT_TRANSLATED,
    val stage: TranslationStage = TranslationStage.IDLE,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
)

data class TranslationQueueStats(
    val doneCount: Int = 0,
    val queueCount: Int = 0,
    val failedCount: Int = 0,
)
