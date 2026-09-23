package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenRouterTranslationEngine(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val model: String = "google/gemini-flash-1.5",
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class OpenRouterRequest(val model: String, val messages: List<Message>)

    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty() || apiKey.isBlank()) return@withContext texts

        val actualModel = if (model.isBlank()) "google/gemini-flash-1.5" else model
        val prompt = "Translate the following list of manga text segments from $fromLang to $toLang. Output ONLY the translated segments separated by '\\n---SEGMENT---\\n' without any extra text or numbering.\n\n" + texts.joinToString("\n---SEGMENT---\n")

        val requestData = OpenRouterRequest(
            model = actualModel,
            messages = listOf(Message(role = "user", content = prompt)),
        )

        val requestBody = json.encodeToString(requestData).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/mihonapp/mihon")
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body.string()
            if (!response.isSuccessful) return@withContext texts

            val root = json.parseToJsonElement(bodyString).jsonObject
            val choices = root["choices"]?.jsonArray
            val firstChoice = choices?.getOrNull(0)?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val outputText = message?.get("content")?.jsonPrimitive?.content ?: ""

            val translatedSegments = outputText.split("\n---SEGMENT---\n")
            if (translatedSegments.size == texts.size) {
                translatedSegments.map { it.trim() }
            } else {
                texts
            }
        } catch (e: Exception) {
            texts
        }
    }
}
