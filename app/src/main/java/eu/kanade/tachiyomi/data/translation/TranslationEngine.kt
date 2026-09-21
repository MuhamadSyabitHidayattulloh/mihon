package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface TranslationEngine {
    suspend fun translate(text: String, from: String, to: String): String
}

class GoogleTranslateEngine(
    private val client: OkHttpClient,
) : TranslationEngine {
    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return text
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=" +
                java.net.URLEncoder.encode(text, "UTF-8")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return text
                val bodyStr = response.body.string()
                val json = Json.parseToJsonElement(bodyStr).jsonArray
                val segments = json[0].jsonArray
                val sb = StringBuilder()
                for (i in 0 until segments.size) {
                    val seg = segments[i].jsonArray
                    if (seg.isNotEmpty()) {
                        sb.append(seg[0].jsonPrimitive.content)
                    }
                }
                sb.toString()
            }
        } catch (e: Exception) {
            text
        }
    }
}

class GeminiTranslateEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
) : TranslationEngine {
    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank() || apiKey.isBlank()) return text
        return try {
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val prompt =
                "Translate the following manga text from $from to $to. Return only the translated text without commentary:\n$text"
            val jsonBody = """
                {
                  "contents": [{
                    "parts": [{"text": ${Json.encodeToString(kotlinx.serialization.serializer(), prompt)}}]
                  }]
                }
            """.trimIndent()
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return text
                val bodyStr = response.body.string()
                val json = Json.parseToJsonElement(bodyStr).jsonObject
                val candidates = json["candidates"]?.jsonArray
                val content = candidates?.get(0)?.jsonObject?.get("content")?.jsonObject
                val parts = content?.get("parts")?.jsonArray
                val resultText = parts?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                resultText?.trim() ?: text
            }
        } catch (e: Exception) {
            text
        }
    }
}

class OpenRouterTranslateEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : TranslationEngine {
    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank() || apiKey.isBlank()) return text
        val modelName = if (model.isBlank()) "google/gemini-2.5-flash" else model
        return try {
            val url = "https://openrouter.ai/api/v1/chat/completions"
            val prompt =
                "Translate the following manga text from $from to $to. Return only the translated text:\n$text"
            val jsonBody = """
                {
                  "model": ${Json.encodeToString(kotlinx.serialization.serializer(), modelName)},
                  "messages": [{"role": "user", "content": ${Json.encodeToString(
                kotlinx.serialization.serializer(),
                prompt,
            )}}]
                }
            """.trimIndent()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return text
                val bodyStr = response.body.string()
                val json = Json.parseToJsonElement(bodyStr).jsonObject
                val choices = json["choices"]?.jsonArray
                val message = choices?.get(0)?.jsonObject?.get("message")?.jsonObject
                val content = message?.get("content")?.jsonPrimitive?.content
                content?.trim() ?: text
            }
        } catch (e: Exception) {
            text
        }
    }
}

class MlKitOfflineTranslateEngine : TranslationEngine {
    override suspend fun translate(text: String, from: String, to: String): String {
        // Fallback or basic dictionary / identity if offline ML-Kit model isn't downloaded
        return text
    }
}
