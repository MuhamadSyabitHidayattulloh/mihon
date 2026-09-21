package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.domain.translation.service.TranslationPreferences
import java.net.URLEncoder

class TranslatorEngine(
    private val preferences: TranslationPreferences,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun translateText(text: String, fromLang: String, toLang: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val engineType = preferences.translatorType.get()
        return@withContext runCatching {
            when (engineType) {
                TranslationPreferences.TYPE_GOOGLE -> translateGoogleWeb(text, fromLang, toLang)
                TranslationPreferences.TYPE_GEMINI -> translateGemini(text, fromLang, toLang)
                TranslationPreferences.TYPE_OPENROUTER -> translateOpenRouter(text, fromLang, toLang)
                TranslationPreferences.TYPE_MLKIT -> translateGoogleWeb(text, fromLang, toLang) // Fallback for ML-Kit
                else -> translateGoogleWeb(text, fromLang, toLang)
            }
        }.getOrElse {
            // Fallback to Google Web Translate on error
            runCatching { translateGoogleWeb(text, fromLang, toLang) }.getOrDefault(text)
        }
    }

    private fun translateGoogleWeb(text: String, fromLang: String, toLang: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val baseUrl = "https://translate.googleapis.com/translate_a/single"
        val url = "$baseUrl?client=gtx&sl=$fromLang&tl=$toLang&dt=t&q=$encodedText"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return text
            val body = response.body.string()
            val jsonArray = json.parseToJsonElement(body).jsonArray
            val sentences = jsonArray[0].jsonArray
            val result = StringBuilder()
            for (sentence in sentences) {
                result.append(sentence.jsonArray[0].jsonPrimitive.content)
            }
            return result.toString()
        }
    }

    private fun translateGemini(text: String, fromLang: String, toLang: String): String {
        val apiKey = preferences.geminiApiKey.get()
        if (apiKey.isBlank()) return translateGoogleWeb(text, fromLang, toLang)

        val model = preferences.geminiModel.get().ifBlank { "gemini-1.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val prompt = "Translate the following comic text from $fromLang to $toLang. " +
            "Output ONLY the translated text without commentary:\n$text"
        val jsonBody = """
            {
              "contents": [{
                "parts":[{"text": ${Json.encodeToString(prompt)}}]
              }]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return translateGoogleWeb(text, fromLang, toLang)
            val body = response.body.string()
            val parsed = json.parseToJsonElement(body).jsonObject
            val candidates = parsed["candidates"]?.jsonArray
            val content = candidates?.getOrNull(0)?.jsonObject?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            return parts?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim() ?: text
        }
    }

    private fun translateOpenRouter(text: String, fromLang: String, toLang: String): String {
        val apiKey = preferences.openRouterApiKey.get()
        if (apiKey.isBlank()) return translateGoogleWeb(text, fromLang, toLang)

        val model = preferences.openRouterModel.get().ifBlank { "google/gemini-flash-1.5" }
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val prompt = "Translate the following comic text from $fromLang to $toLang. " +
            "Output ONLY the translated text without commentary:\n$text"
        val jsonBody = """
            {
              "model": "$model",
              "messages": [
                {"role": "user", "content": ${Json.encodeToString(prompt)}}
              ]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/mihonapp/mihon")
            .header("X-Title", "Mihon Translation")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return translateGoogleWeb(text, fromLang, toLang)
            val body = response.body.string()
            val parsed = json.parseToJsonElement(body).jsonObject
            val choices = parsed["choices"]?.jsonArray
            val message = choices?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject
            return message?.get("content")?.jsonPrimitive?.content?.trim() ?: text
        }
    }
}
