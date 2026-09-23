package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiTranslationEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(texts: List<String>, sourceLang: String, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (apiKey.isBlank()) return texts

        val modelName = model.ifBlank { "gemini-1.5-flash" }
        val prompt = buildString {
            append("Translate the following manga speech bubble text blocks from $sourceLang to $targetLang.\n")
            append(
                "Return ONLY a JSON array of strings corresponding to each input text in order, with no markdown code blocks.\n",
            )
            append("Input texts:\n")
            texts.forEachIndexed { index, text ->
                append("[$index] $text\n")
            }
        }

        val requestBodyJson = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(
                                buildJsonObject {
                                    put("text", prompt)
                                },
                            )
                        }
                    },
                )
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
            }
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return texts
                val bodyStr = response.body.string()
                val responseJson = json.parseToJsonElement(bodyStr).jsonObject
                val candidates = responseJson["candidates"]?.jsonArray ?: return texts
                val firstCandidate = candidates.getOrNull(0)?.jsonObject ?: return texts
                val content = firstCandidate["content"]?.jsonObject ?: return texts
                val parts = content["parts"]?.jsonArray ?: return texts
                val textPart = parts.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return texts

                val translatedArray = json.parseToJsonElement(textPart.trim()).jsonArray
                translatedArray.map { it.jsonPrimitive.content }
            }
        } catch (_: Exception) {
            texts
        }
    }
}
