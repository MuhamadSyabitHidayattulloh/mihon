package tachiyomi.domain.translation.model

enum class TranslationEngine(val key: String, val displayName: String) {
    MLKIT("mlkit", "ML Kit (Offline)"),
    GOOGLE("google", "Google Translate (Web)"),
    GEMINI("gemini", "Gemini AI"),
    OPENROUTER("openrouter", "OpenRouter"),
    ;

    companion object {
        fun fromKey(key: String): TranslationEngine = values().find { it.key == key } ?: MLKIT
    }
}

enum class TranslationLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    CHINESE("zh", "Chinese"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    INDONESIAN("id", "Indonesian"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    RUSSIAN("ru", "Russian"),
    PORTUGUESE("pt", "Portuguese"),
    ITALIAN("it", "Italian"),
    THAI("th", "Thai"),
    VIETNAMESE("vi", "Vietnamese"),
    ARABIC("ar", "Arabic"),
    ;

    companion object {
        fun fromCode(code: String): TranslationLanguage = values().find { it.code == code } ?: ENGLISH
    }
}

enum class TranslationStatus {
    NOT_TRANSLATED,
    QUEUED,
    TRANSLATING,
    TRANSLATED,
    ERROR,
}

data class TranslationQueueItem(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val chapterNumber: Float,
    val status: TranslationStatus = TranslationStatus.QUEUED,
    val progress: Float = 0f,
    val totalPages: Int = 0,
    val translatedPages: Int = 0,
    val error: String? = null,
)

enum class OnnxModelType(val filename: String) {
    DETECTOR("detector-v4-s_int8.onnx"),
    OCR_REC("PP-OCRv6_small_rec.onnx"),
    OCR_KEYS("PP-OCRv6_small_rec.txt"),
    INPAINTING("aot.onnx"),
}
