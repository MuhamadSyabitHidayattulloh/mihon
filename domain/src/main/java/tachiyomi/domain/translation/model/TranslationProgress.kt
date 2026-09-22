package tachiyomi.domain.translation.model

import kotlinx.serialization.Serializable

enum class TranslationState {
    NOT_TRANSLATED,
    QUEUED,
    TRANSLATING,
    TRANSLATED,
    ERROR,
}

enum class TranslationStage {
    DETECTION,
    OCR,
    CLEANING,
    TRANSLATION,
    CANVAS_RENDER,
    IDLE,
}

@Serializable
data class TranslationProgress(
    val chapterId: Long,
    val state: TranslationState = TranslationState.NOT_TRANSLATED,
    val stage: TranslationStage = TranslationStage.IDLE,
    val progressPages: Int = 0,
    val totalPages: Int = 0,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
)
