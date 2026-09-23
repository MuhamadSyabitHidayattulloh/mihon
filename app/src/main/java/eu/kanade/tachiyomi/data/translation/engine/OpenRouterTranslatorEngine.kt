package eu.kanade.tachiyomi.data.translation.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.domain.translation.model.TranslationLanguage

class OpenRouterTranslatorEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val modelName: String = "google/gemini-flash-1.5",
) : TranslationEngineProvider {

    override suspend fun translate(
        text: String,
        sourceLang: TranslationLanguage,
        targetLang: TranslationLanguage,
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (text.isBlank() || apiKey.isBlank()) return@withContext text

        val url = "https://openrouter.ai/api/v1/chat/completions"
        val prompt =
            "Translate the following comic text from ${sourceLang.displayName} to ${targetLang.displayName}. " +
                "Provide ONLY the translation text without any extra notes, quotes, or markdown:\n\n$text"

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    },
                ),
            )
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://mihon.app")
            .header("X-Title", "Mihon")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext text
            val responseBody = response.body.string()
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices") ?: return@withContext text
            if (choices.length() == 0) return@withContext text
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message") ?: return@withContext text
            message.optString("content", text).trim()
        }
    }
}
