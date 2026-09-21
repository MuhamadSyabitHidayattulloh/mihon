package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

interface TranslationEngine {
    suspend fun translate(text: String, sourceLang: String, targetLang: String): String
}

class GoogleTranslateEngine(private val okHttpClient: OkHttpClient) : TranslationEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        if (text.isBlank()) return text
        return withContext(Dispatchers.IO) {
            val src = if (sourceLang == "auto") "auto" else sourceLang
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            val baseUrl = "https://translate.googleapis.com/translate_a/single"
            val url = "$baseUrl?client=gtx&sl=$src&tl=$targetLang&dt=t&q=$encoded"
            val request = Request.Builder().url(url).build()
            try {
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext text
                val body = response.body?.string() ?: return@withContext text
                val jsonArray = JSONArray(body)
                val sentences = jsonArray.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    sb.append(sentences.getJSONArray(i).getString(0))
                }
                sb.toString()
            } catch (e: Exception) {
                text
            }
        }
    }
}

class GeminiTranslationEngine(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : TranslationEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        if (text.isBlank() || apiKey.isBlank()) return text
        return withContext(Dispatchers.IO) {
            val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
            val url = "$baseUrl/$model:generateContent?key=$apiKey"
            val prompt = "Translate to $targetLang:\n$text"
            val jsonBody = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                        },
                    ),
                )
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext text
                val responseString = response.body?.string() ?: return@withContext text
                val jsonResponse = JSONObject(responseString)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text").trim()
                    }
                }
                text
            } catch (e: Exception) {
                text
            }
        }
    }
}

class OpenRouterTranslationEngine(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : TranslationEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        if (text.isBlank() || apiKey.isBlank()) return text
        return withContext(Dispatchers.IO) {
            val url = "https://openrouter.ai/api/v1/chat/completions"
            val prompt = "Translate to $targetLang:\n$text"
            val jsonBody = JSONObject().apply {
                put("model", if (model.isBlank()) "google/gemini-2.5-flash" else model)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        },
                    ),
                )
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext text
                val responseString = response.body?.string() ?: return@withContext text
                val jsonResponse = JSONObject(responseString)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    return@withContext message.getString("content").trim()
                }
                text
            } catch (e: Exception) {
                text
            }
        }
    }
}

class MlKitTranslationEngine : TranslationEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        return text
    }
}
