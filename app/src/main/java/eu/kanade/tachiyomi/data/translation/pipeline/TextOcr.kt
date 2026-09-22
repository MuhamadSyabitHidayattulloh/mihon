package eu.kanade.tachiyomi.data.translation.pipeline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import java.io.File
import java.nio.FloatBuffer

class TextOcr {

    private val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!?,.:;-'\" "

    fun recognize(
        bitmap: Bitmap,
        detectedBlocks: List<DetectedBlock>,
        modelFile: File?,
    ): List<TextBlock> {
        if (detectedBlocks.isEmpty()) return emptyList()

        if (modelFile != null && modelFile.exists() && modelFile.length() > 0) {
            try {
                return recognizeWithOnnx(bitmap, detectedBlocks, modelFile)
            } catch (_: Exception) {
                // Fall back if ONNX OCR fails
            }
        }
        return recognizeWithFallback(bitmap, detectedBlocks)
    }

    private fun recognizeWithOnnx(
        bitmap: Bitmap,
        detectedBlocks: List<DetectedBlock>,
        modelFile: File,
    ): List<TextBlock> {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions()
        val session = env.createSession(modelFile.absolutePath, sessionOptions)

        val resultBlocks = mutableListOf<TextBlock>()
        try {
            for (block in detectedBlocks) {
                val box = block.boundingBox
                val cropLeft = box.left.coerceIn(0f, bitmap.width.toFloat()).toInt()
                val cropTop = box.top.coerceIn(0f, bitmap.height.toFloat()).toInt()
                val cropRight = box.right.coerceIn(cropLeft + 1f, bitmap.width.toFloat()).toInt()
                val cropBottom = box.bottom.coerceIn(cropTop + 1f, bitmap.height.toFloat()).toInt()

                val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)
                val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)

                val cropBitmap = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
                val targetHeight = 48
                val targetWidth = 320
                val scaled = Bitmap.createScaledBitmap(cropBitmap, targetWidth, targetHeight, true)

                val floatBuffer = FloatBuffer.allocate(1 * 3 * targetHeight * targetWidth)
                val pixels = IntArray(targetWidth * targetHeight)
                scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

                for (c in 0 until 3) {
                    for (i in 0 until targetHeight * targetWidth) {
                        val px = pixels[i]
                        val channelVal = when (c) {
                            0 -> Color.red(px)
                            1 -> Color.green(px)
                            else -> Color.blue(px)
                        } / 255.0f
                        floatBuffer.put(channelVal)
                    }
                }
                floatBuffer.rewind()

                val inputTensor = OnnxTensor.createTensor(
                    env,
                    floatBuffer,
                    longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()),
                )

                val results = session.run(mapOf(session.inputNames.first() to inputTensor))
                val recognizedChars = StringBuilder()

                if (results.size() > 0) {
                    val outputValue = results.get(0)
                    if (outputValue is OnnxTensor) {
                        val outFloatBuffer = outputValue.floatBuffer
                        val totalCapacity = outFloatBuffer.capacity()
                        var idx = 0
                        while (idx < totalCapacity) {
                            var maxIdx = 0
                            var maxVal = Float.NEGATIVE_INFINITY
                            val stepSize = (alphabet.length + 1).coerceAtMost(totalCapacity - idx)
                            for (c in 0 until stepSize) {
                                val valAt = outFloatBuffer.get(idx + c)
                                if (valAt > maxVal) {
                                    maxVal = valAt
                                    maxIdx = c
                                }
                            }
                            if (maxIdx > 0 && maxIdx <= alphabet.length) {
                                recognizedChars.append(alphabet[maxIdx - 1])
                            }
                            idx += stepSize.coerceAtLeast(1)
                        }
                    }
                }

                inputTensor.close()
                results.close()

                val text = recognizedChars.toString().trim().ifBlank { "..." }
                resultBlocks.add(TextBlock(boundingBox = RectF(box), originalText = text))
            }
        } finally {
            session.close()
        }
        return resultBlocks
    }

    private fun recognizeWithFallback(
        bitmap: Bitmap,
        detectedBlocks: List<DetectedBlock>,
    ): List<TextBlock> {
        return detectedBlocks.mapIndexed { index, block ->
            TextBlock(
                boundingBox = RectF(block.boundingBox),
                originalText = "Bubble ${index + 1}",
            )
        }
    }
}
