package eu.kanade.tachiyomi.data.translation.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.domain.translation.model.TranslationLanguage

class GeminiTranslatorEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val modelName: String = "gemini-1.5-flash",
) : TranslationEngineProvider {

    override suspend fun translate(
        text: String,
        sourceLang: TranslationLanguage,
        targetLang: TranslationLanguage,
    ): String {
        if (text.isBlank() || apiKey.isBlank()) return text

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val prompt =
            "Translate the following comic text from ${sourceLang.displayName} to ${targetLang.displayName}. " +
                "Provide ONLY the translation text without any extra notes, quotes, or markdown:\n\n$text"

        val jsonBody = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("text", prompt)
                                },
                            ),
                        )
                    },
                ),
            )
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return text
            val responseBody = response.body.string()
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates") ?: return text
            if (candidates.length() == 0) return text
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return text
            val parts = content.optJSONArray("parts") ?: return text
            if (parts.length() == 0) return text
            return parts.getJSONObject(0).optString("text", text).trim()
        }
    }
}
