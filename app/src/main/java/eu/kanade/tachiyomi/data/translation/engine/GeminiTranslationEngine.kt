package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiTranslationEngine(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Content(val parts: List<Part>)

    @Serializable
    private data class Part(val text: String)

    @Serializable
    private data class GeminiRequest(val contents: List<Content>)

    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty() || apiKey.isBlank()) return@withContext texts

        val actualModel = if (model.isBlank()) "gemini-2.5-flash" else model
        val prompt = "You are a professional comic and manga translator. Translate the following list of text segments from language $fromLang to language $toLang. Preserve the exact number of lines and order. Output ONLY the translated strings, line by line, without any markdown or numbering.\n\n" + texts.joinToString("\n---SEGMENT---\n")

        val requestBodyData = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
        )

        val requestBody = json.encodeToString(requestBodyData).toRequestBody("application/json".toMediaType())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$actualModel:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body.string()
            if (!response.isSuccessful) return@withContext texts

            val root = json.parseToJsonElement(bodyString).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val firstCandidate = candidates?.getOrNull(0)?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val outputText = parts?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

            val translatedSegments = outputText.split("\n---SEGMENT---\n")
            if (translatedSegments.size == texts.size) {
                translatedSegments.map { it.trim() }
            } else {
                // Fallback split line by line
                val lines = outputText.lines().filter { it.isNotBlank() }
                if (lines.size == texts.size) lines else texts
            }
        } catch (e: Exception) {
            texts
        }
    }
}
