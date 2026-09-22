package tachiyomi.domain.translation.model

enum class TranslationState {
    NOT_TRANSLATED,
    QUEUED,
    TRANSLATING,
    TRANSLATED,
    ERROR,
}

data class TranslationProgress(
    val chapterId: Long,
    val state: TranslationState,
    val currentStep: Step = Step.IDLE,
    val progressPagesDone: Int = 0,
    val totalPages: Int = 0,
    val queueCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null,
    val logs: List<String> = emptyList(),
) {
    enum class Step {
        IDLE,
        DETECTION,
        OCR,
        TRANSLATION,
        CLEANING,
        RENDERER,
        FINISHED,
        FAILED,
    }
}
