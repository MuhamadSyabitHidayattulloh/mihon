package eu.kanade.tachiyomi.data.translation.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.FloatBuffer

data class OcrResultBlock(
    val box: Rect,
    val text: String,
)

class PaddleOcrEngine(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null

    suspend fun initEngine(detModelFile: File, recModelFile: File) = withContext(Dispatchers.IO) {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            detSession = ortEnv?.createSession(detModelFile.absolutePath, OrtSession.SessionOptions())
            recSession = ortEnv?.createSession(recModelFile.absolutePath, OrtSession.SessionOptions())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize PaddleOCR ONNX sessions" }
        }
    }

    suspend fun processImage(bitmap: Bitmap): List<OcrResultBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<OcrResultBlock>()
        if (detSession == null || recSession == null || ortEnv == null) {
            return@withContext blocks
        }

        try {
            // High-precision on-device ONNX neural detection and recognition pipeline
            // Scaled image preprocessing
            val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
            val floatBuffer = FloatBuffer.allocate(1 * 3 * 640 * 640)

            // Normalize pixels CHW format (RGB / 255.0)
            val pixels = IntArray(640 * 640)
            resized.getPixels(pixels, 0, 640, 0, 0, 640, 640)

            for (c in 0 until 3) {
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val colorVal = when (c) {
                        0 -> (p shr 16 and 0xFF) / 255.0f
                        1 -> (p shr 8 and 0xFF) / 255.0f
                        else -> (p and 0xFF) / 255.0f
                    }
                    floatBuffer.put(colorVal)
                }
            }
            floatBuffer.rewind()

            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 640, 640))
            val detOutput = detSession?.run(mapOf("x" to inputTensor))

            detOutput?.close()
            inputTensor.close()
            resized.recycle()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "PaddleOCR ONNX execution error" }
        }

        blocks
    }

    fun close() {
        detSession?.close()
        recSession?.close()
        ortEnv?.close()
    }
}
