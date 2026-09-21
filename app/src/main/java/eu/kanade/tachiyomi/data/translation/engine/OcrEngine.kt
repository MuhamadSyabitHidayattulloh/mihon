package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine {

    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val japaneseRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val chineseRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    suspend fun recognizeText(bitmap: Bitmap, fromLang: String): List<TextRegion> {
        val recognizer = when (fromLang) {
            "ja" -> japaneseRecognizer
            "zh" -> chineseRecognizer
            else -> latinRecognizer
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText: Text = recognizer.process(image).awaitTask()

        val regions = mutableListOf<TextRegion>()
        for (block in visionText.textBlocks) {
            val rect = block.boundingBox ?: Rect(0, 0, bitmap.width, bitmap.height)
            val text = block.text.trim()
            if (text.isNotBlank()) {
                regions.add(TextRegion(rect = rect, sampleText = text))
            }
        }

        return regions
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { exception -> continuation.resumeWithException(exception) }
        addOnCanceledListener { continuation.cancel() }
    }
}
