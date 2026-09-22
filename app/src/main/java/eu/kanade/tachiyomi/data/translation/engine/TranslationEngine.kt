package eu.kanade.tachiyomi.data.translation.engine

interface TranslationEngine {
    suspend fun translate(texts: List<String>, sourceLang: String, targetLang: String): List<String>
}
