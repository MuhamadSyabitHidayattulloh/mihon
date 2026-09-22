package eu.kanade.tachiyomi.data.translation.engine

interface TranslatorEngine {
    suspend fun translate(text: String, from: String, to: String): String
    suspend fun translateBatch(texts: List<String>, from: String, to: String): List<String> {
        return texts.map { translate(it, from, to) }
    }
}
