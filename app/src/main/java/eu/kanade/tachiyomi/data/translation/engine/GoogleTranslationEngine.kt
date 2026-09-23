package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class GoogleTranslationEngine(
    private val client: OkHttpClient,
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(texts: List<String>, sourceLang: String, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()

        return texts.map { text ->
            if (text.isBlank()) {
                text
            } else {
                translateSingleText(text, sourceLang, targetLang)
            }
        }
    }

    private fun translateSingleText(text: String, sourceLang: String, targetLang: String): String {
        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return text
            val body = response.body.string()
            val jsonArray = json.parseToJsonElement(body).jsonArray
            val sentences = jsonArray.getOrNull(0)?.jsonArray ?: return text

            val translatedBuilder = StringBuilder()
            for (sentence in sentences) {
                val sentenceArray = sentence.jsonArray
                val part = sentenceArray.getOrNull(0)?.jsonPrimitive?.content
                if (part != null) {
                    translatedBuilder.append(part)
                }
            }
            return if (translatedBuilder.isNotEmpty()) translatedBuilder.toString() else text
        }
    }
}
