package eu.kanade.tachiyomi.data.translation.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ComicTextDetector(private val context: Context) {

    private val paddleEngine = PaddleOcrEngine(context)

    suspend fun detectComicText(bitmap: Bitmap): List<OcrResultBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<OcrResultBlock>()
        try {
            blocks.addAll(paddleEngine.processImage(bitmap))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "PaddleOCR execution error" }
        }

        if (blocks.isEmpty()) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizers = listOf(
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            )

            for (recognizer in recognizers) {
                try {
                    val visionText = suspendCancellableCoroutine { continuation ->
                        recognizer.process(image)
                            .addOnSuccessListener { text -> continuation.resume(text) }
                            .addOnFailureListener { e -> continuation.resumeWithException(e) }
                    }
                    for (block in visionText.textBlocks) {
                        val box = block.boundingBox ?: continue
                        if (block.text.isNotBlank()) {
                            blocks.add(OcrResultBlock(box, block.text))
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "ML-Kit text recognition error" }
                } finally {
                    try {
                        recognizer.close()
                    } catch (_: Exception) {}
                }
            }
        }
        blocks
    }
}
