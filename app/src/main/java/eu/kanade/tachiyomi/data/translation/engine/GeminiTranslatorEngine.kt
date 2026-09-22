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

class GeminiTranslatorEngine(
    private val networkHelper: NetworkHelper,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String,
) : TranslatorEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return text
        val apiKey = apiKeyProvider().ifBlank { return text }
        val model = modelProvider().ifBlank { "gemini-1.5-flash" }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val prompt =
                    "Translate the following manga dialogue/text from $from to $to. " +
                        "Reply ONLY with the translated text without commentary or quotes:\n$text"

                val jsonBody = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            putJsonArray("parts") {
                                addJsonObject {
                                    put("text", prompt)
                                }
                            }
                        }
                    }
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext text
                    val responseStr = response.body.string()
                    val root = json.parseToJsonElement(responseStr).jsonObject
                    val candidates = root["candidates"]?.jsonArray
                    val content = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                    val parts = content?.get("parts")?.jsonArray
                    val result = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    result?.trim()?.ifBlank { text } ?: text
                }
            } catch (e: Exception) {
                text
            }
        }
    }
}
