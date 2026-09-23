package eu.kanade.tachiyomi.data.translation.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.domain.translation.model.TranslationLanguage

class GoogleTranslatorEngine(
    private val client: OkHttpClient,
) : TranslationEngineProvider {

    override suspend fun translate(
        text: String,
        sourceLang: TranslationLanguage,
        targetLang: TranslationLanguage,
    ): String {
        if (text.isBlank()) return text

        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", sourceLang.code)
            .addQueryParameter("tl", targetLang.code)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return text
            val body = response.body.string()
            val jsonArray = JSONArray(body)
            val sentences = jsonArray.getJSONArray(0)
            val result = StringBuilder()
            for (i in 0 until sentences.length()) {
                val sentence = sentences.getJSONArray(i)
                result.append(sentence.getString(0))
            }
            return result.toString()
        }
    }
}
