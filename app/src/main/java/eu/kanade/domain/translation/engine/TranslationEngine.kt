package eu.kanade.domain.translation.engine

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.domain.translation.service.TranslationPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resumeWithException

interface TranslationEngine {
    suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String>
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result, null) }
        addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    }

class MlKitTranslationEngine : TranslationEngine {
    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()

        val sourceLang = mapLanguageCode(fromLang)
        val targetLang = mapLanguageCode(toLang)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()

        val translator = Translation.getClient(options)

        return try {
            translator.downloadModelIfNeeded().awaitTask()
            texts.map { text ->
                if (text.isBlank()) {
                    ""
                } else {
                    translator.translate(text).awaitTask()
                }
            }
        } finally {
            translator.close()
        }
    }

    private fun mapLanguageCode(code: String): String {
        return when (code.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "zh", "cn" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "id" -> TranslateLanguage.INDONESIAN
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "ru" -> TranslateLanguage.RUSSIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "it" -> TranslateLanguage.ITALIAN
            "vi" -> TranslateLanguage.VIETNAMESE
            "th" -> TranslateLanguage.THAI
            "ar" -> TranslateLanguage.ARABIC
            else -> TranslateLanguage.fromLanguageCode(code) ?: TranslateLanguage.ENGLISH
        }
    }
}

class GoogleWebTranslationEngine(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) : TranslationEngine {
    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()

            texts.map { text ->
                if (text.isBlank()) return@map ""

                val sl = if (fromLang == "zh") "zh-CN" else fromLang
                val tl = if (toLang == "zh") "zh-CN" else toLang
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=" +
                    java.net.URLEncoder.encode(text, "UTF-8")

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@map text
                    val body = response.body.string()
                    val outerArray = json.parseToJsonElement(body).jsonArray
                    val sentences = outerArray[0].jsonArray
                    val sb = StringBuilder()
                    for (sentence in sentences) {
                        val part = sentence.jsonArray[0].jsonPrimitive.content
                        sb.append(part)
                    }
                    sb.toString()
                }
            }
        }
}

class GeminiTranslationEngine(
    private val networkHelper: NetworkHelper,
    private val preferences: TranslationPreferences,
    private val json: Json,
) : TranslationEngine {
    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()

            val apiKey = preferences.geminiApiKey.get()
            val model = preferences.geminiModel.get().ifBlank { "gemini-2.5-flash" }

            if (apiKey.isBlank()) {
                throw IllegalStateException("Gemini API Key is missing. Please set it in Settings -> Translations.")
            }

            val prompt = "Translate the following comic bubble texts from $fromLang to $toLang. " +
                "Return JSON array of strings in exact order without markdown formatting:\n" +
                json.encodeToString(ListSerializer(String.serializer()), texts)

            val requestBodyJson = """
                {
                  "contents": [{
                    "parts": [{"text": ${json.encodeToString(prompt)}}]
                  }]
                }
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body.string()
                    throw IllegalStateException("Gemini API Error (${response.code}): $err")
                }
                val body = response.body.string()
                val jsonEl = json.parseToJsonElement(body)
                val responseText = jsonEl.jsonObject["candidates"]
                    ?.jsonArray?.get(0)?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.get(0)?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: ""

                parseTranslatedList(responseText, texts)
            }
        }

    private fun parseTranslatedList(responseText: String, fallback: List<String>): List<String> {
        val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
        return try {
            json.decodeFromString<List<String>>(cleanJson)
        } catch (_: Exception) {
            fallback
        }
    }
}

class OpenRouterTranslationEngine(
    private val networkHelper: NetworkHelper,
    private val preferences: TranslationPreferences,
    private val json: Json,
) : TranslationEngine {
    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()

            val apiKey = preferences.openRouterApiKey.get()
            val model = preferences.openRouterModel.get().ifBlank { "google/gemini-2.5-flash" }

            if (apiKey.isBlank()) {
                throw IllegalStateException("OpenRouter API Key is missing. Please set it in Settings -> Translations.")
            }

            val prompt = "Translate the following comic bubble texts from $fromLang to $toLang. " +
                "Return JSON array of strings in exact order without markdown code block syntax:\n" +
                json.encodeToString(ListSerializer(String.serializer()), texts)

            val requestBodyJson = """
                {
                  "model": "$model",
                  "messages": [
                    {"role": "user", "content": ${json.encodeToString(prompt)}}
                  ]
                }
            """.trimIndent()

            val url = "https://openrouter.ai/api/v1/chat/completions"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://mihon.app")
                .header("X-Title", "Mihon")
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body.string()
                    throw IllegalStateException("OpenRouter API Error (${response.code}): $err")
                }
                val body = response.body.string()
                val jsonEl = json.parseToJsonElement(body)
                val responseText = jsonEl.jsonObject["choices"]
                    ?.jsonArray?.get(0)?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content ?: ""

                val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                try {
                    json.decodeFromString<List<String>>(cleanJson)
                } catch (_: Exception) {
                    texts
                }
            }
        }
}
