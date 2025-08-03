package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.core.common.util.lang.awaitSingle
import uy.kohesive.injekt.injectLazy

class TranslatorManager(private val context: Context) {

    private val client: OkHttpClient by injectLazy()

    fun detectText(bitmap: Bitmap): Flow<List<Text.TextBlock>> = flow {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).awaitSingle()
        emit(result.textBlocks)
    }

    fun translate(text: String, from: String, to: String): Flow<String> = flow {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", from)
            .addQueryParameter("tl", to)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = JSONArray(response.body.string())
        val translatedText = json.getJSONArray(0).getJSONArray(0).getString(0)
        emit(translatedText)
    }

    fun drawTranslatedText(
        bitmap: Bitmap,
        texts: List<Text.TextBlock>,
        translatedTexts: List<String>,
    ): Bitmap {
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(newBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        texts.forEachIndexed { index, textBlock ->
            textBlock.boundingBox?.let {
                canvas.drawRect(it, paint)
            }
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
        }
        texts.forEachIndexed { index, textBlock ->
            textBlock.boundingBox?.let {
                canvas.drawText(
                    translatedTexts[index],
                    it.left.toFloat(),
                    it.bottom.toFloat(),
                    textPaint,
                )
            }
        }
        return newBitmap
    }
}
