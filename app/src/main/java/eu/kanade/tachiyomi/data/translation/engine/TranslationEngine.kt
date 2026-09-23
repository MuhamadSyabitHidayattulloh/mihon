package eu.kanade.tachiyomi.data.translation.engine

interface TranslationEngine {
    suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String>
}
