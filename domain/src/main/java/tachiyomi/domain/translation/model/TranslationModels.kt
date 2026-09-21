package tachiyomi.domain.translation.model

import kotlinx.serialization.Serializable

enum class TranslationStage {
    DETECTION,
    OCR,
    INPAINTING,
    TRANSLATION,
    RENDERING,
    STORAGE,
}

sealed interface TranslationState {
    data object NotTranslated : TranslationState
    data object Queued : TranslationState
    data class Processing(
        val stage: TranslationStage,
        val currentPage: Int,
        val totalPages: Int,
        val progress: Float,
    ) : TranslationState
    data object Completed : TranslationState
    data class Failed(
        val stage: TranslationStage,
        val message: String,
        val exception: Throwable? = null,
    ) : TranslationState
    data object Cancelled : TranslationState
}

@Serializable
data class TranslationMetadata(
    val sourceLanguage: String,
    val targetLanguage: String,
    val translatorEngine: String,
    val font: String,
    val modelVersion: String = "1.0",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED",
)
