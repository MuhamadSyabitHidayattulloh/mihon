package eu.kanade.tachiyomi.data.translation.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max

class TextOcr(
    private val modelFile: File,
    private val keysFile: File,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val keys: List<String> = if (keysFile.exists()) {
        keysFile.readLines()
    } else {
        emptyList()
    }

    init {
        if (modelFile.exists()) {
            session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        }
    }

    fun recognize(cropBitmap: Bitmap): String {
        val sess = session ?: return ""
        if (cropBitmap.width == 0 || cropBitmap.height == 0) return ""

        val targetHeight = 48
        val targetWidth = max(48, (cropBitmap.width * (48.0f / cropBitmap.height)).toInt())
        val scaled = Bitmap.createScaledBitmap(cropBitmap, targetWidth, targetHeight, true)

        val inputBuffer = FloatBuffer.allocate(1 * 3 * targetHeight * targetWidth)
        val intValues = IntArray(targetWidth * targetHeight)
        scaled.getPixels(intValues, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        // Standard PP-OCR normalization: (x / 255 - 0.5) / 0.5
        for (c in 0..2) {
            for (i in 0 until targetWidth * targetHeight) {
                val pixel = intValues[i]
                val channelVal = when (c) {
                    0 -> (pixel shr 16 and 0xFF)
                    1 -> (pixel shr 8 and 0xFF)
                    else -> (pixel and 0xFF)
                }
                val norm = ((channelVal / 255.0f) - 0.5f) / 0.5f
                inputBuffer.put(norm)
            }
        }
        inputBuffer.rewind()

        val inputName = sess.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(
            env,
            inputBuffer,
            longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()),
        )

        val recognizedText = StringBuilder()
        try {
            sess.run(mapOf(inputName to tensor)).use { result ->
                val output = result.get(0).value
                if (output is Array<*>) {
                    // CTC decoding
                    val preds = output as Array<Array<FloatArray>>
                    var prevIndex = -1
                    for (step in preds[0]) {
                        var maxIdx = 0
                        var maxVal = step[0]
                        for (j in 1 until step.size) {
                            if (step[j] > maxVal) {
                                maxVal = step[j]
                                maxIdx = j
                            }
                        }
                        if (maxIdx != 0 && maxIdx != prevIndex) {
                            val keyIdx = maxIdx - 1
                            if (keyIdx in keys.indices) {
                                recognizedText.append(keys[keyIdx])
                            }
                        }
                        prevIndex = maxIdx
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tensor.close()
        }

        return recognizedText.toString()
    }

    fun close() {
        session?.close()
    }
}
