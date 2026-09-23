package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class GoogleWebTranslationEngine(
    private val okHttpClient: OkHttpClient,
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        val source = if (fromLang == "zh") "zh-CN" else fromLang
        val target = if (toLang == "zh") "zh-CN" else toLang

        texts.map { text ->
            if (text.isBlank()) return@map ""

            val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", source)
                .addQueryParameter("tl", target)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body.string()

                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val array = json.parseToJsonElement(responseBody).jsonArray
                    val sentences = array[0].jsonArray
                    val sb = StringBuilder()
                    for (sentence in sentences) {
                        val part = sentence.jsonArray[0].jsonPrimitive.content
                        sb.append(part)
                    }
                    sb.toString()
                } else {
                    text
                }
            } catch (e: Exception) {
                text
            }
        }
    }
}
