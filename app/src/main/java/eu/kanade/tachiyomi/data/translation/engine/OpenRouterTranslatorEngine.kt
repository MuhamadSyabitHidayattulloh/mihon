package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenRouterTranslatorEngine(
    private val networkHelper: NetworkHelper,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String,
) : TranslatorEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return text
        val apiKey = apiKeyProvider().ifBlank { return text }
        val model = modelProvider().ifBlank { "google/gemini-2.0-flash-lite-preview-02-05:free" }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://openrouter.ai/api/v1/chat/completions"
                val jsonBody = buildJsonObject {
                    put("model", model)
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                "You are a professional comic translator. Translate text from $from to $to. Output ONLY the translated text without commentary.",
                            )
                        }
                        addJsonObject {
                            put("role", "user")
                            put("content", text)
                        }
                    }
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext text
                    val responseStr = response.body.string()
                    val root = json.parseToJsonElement(responseStr).jsonObject
                    val choices = root["choices"]?.jsonArray
                    val message = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                    val content = message?.get("content")?.jsonPrimitive?.content
                    content?.trim()?.ifBlank { text } ?: text
                }
            } catch (e: Exception) {
                text
            }
        }
    }
}
