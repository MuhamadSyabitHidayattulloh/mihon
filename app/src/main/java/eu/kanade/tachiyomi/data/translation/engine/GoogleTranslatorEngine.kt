package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class GoogleTranslatorEngine(
    private val networkHelper: NetworkHelper,
) : TranslatorEngine {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(text: String, from: String, to: String): String {
        if (text.isBlank()) return text
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                    .addQueryParameter("client", "gtx")
                    .addQueryParameter("sl", from)
                    .addQueryParameter("tl", to)
                    .addQueryParameter("dt", "t")
                    .addQueryParameter("q", text)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext text
                    val bodyString = response.body.string()
                    val array = json.parseToJsonElement(bodyString).jsonArray
                    val sentences = array[0].jsonArray
                    val sb = StringBuilder()
                    for (sentence in sentences) {
                        val parts = sentence.jsonArray
                        if (parts.isNotEmpty()) {
                            sb.append(parts[0].jsonPrimitive.content)
                        }
                    }
                    sb.toString().ifBlank { text }
                }
            } catch (e: Exception) {
                text
            }
        }
    }
}
