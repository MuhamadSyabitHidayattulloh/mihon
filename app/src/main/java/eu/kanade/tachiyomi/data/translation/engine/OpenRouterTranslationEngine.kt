package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenRouterTranslationEngine(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(texts: List<String>, sourceLang: String, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (apiKey.isBlank()) return texts

        val modelName = model.ifBlank { "google/gemini-2.5-flash" }
        val prompt = buildString {
            append("Translate the following manga speech bubble text blocks from $sourceLang to $targetLang.\n")
            append(
                "Return ONLY a JSON array of strings corresponding to each input text in order, with no markdown formatting.\n",
            )
            append("Input texts:\n")
            texts.forEachIndexed { index, text ->
                append("[$index] $text\n")
            }
        }

        val requestBodyJson = buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    },
                )
            }
        }.toString()

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return texts
                val bodyStr = response.body.string()
                val responseJson = json.parseToJsonElement(bodyStr).jsonObject
                val choices = responseJson["choices"]?.jsonArray ?: return texts
                val firstChoice = choices.getOrNull(0)?.jsonObject ?: return texts
                val message = firstChoice["message"]?.jsonObject ?: return texts
                val content = message["content"]?.jsonPrimitive?.content ?: return texts

                val jsonContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val translatedArray = json.parseToJsonElement(jsonContent).jsonArray
                translatedArray.map { it.jsonPrimitive.content }
            }
        } catch (_: Exception) {
            texts
        }
    }
}
