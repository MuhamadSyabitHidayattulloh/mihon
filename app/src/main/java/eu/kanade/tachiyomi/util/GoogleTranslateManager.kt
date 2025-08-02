package eu.kanade.tachiyomi.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class GoogleTranslateManager {

    private val client = OkHttpClient()

    suspend fun translateText(text: String, targetLang: String = "id"): String = withContext(Dispatchers.IO) {
        val url = "https://translate.google.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=" + text.encodeURL()
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val jsonArray = JSONArray(responseBody)
                val translatedText = jsonArray.getJSONArray(0).getJSONArray(0).getString(0)
                translatedText
            } else {
                Log.e("GoogleTranslateManager", "Translation failed: ${response.code} - ${response.message}")
                ""
            }
        } catch (e: Exception) {
            Log.e("GoogleTranslateManager", "Translation error: ${e.message}", e)
            ""
        }
    }

    private fun String.encodeURL(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}

