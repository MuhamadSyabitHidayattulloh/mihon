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
                val outputTensor = results.firstOrNull()?.value as? OnnxTensor
                val recognizedChars = StringBuilder()

                if (outputTensor != null) {
                    val outBuffer = outputTensor.floatBuffer
                    var lastIdx = -1
                    val numClasses = alphabet.length + 1
                    val steps = outBuffer.capacity() / numClasses

                    for (s in 0 until steps) {
                        var maxVal = -Float.MAX_VALUE
                        var maxIdx = 0
                        for (c in 0 until numClasses) {
                            val idx = s * numClasses + c
                            if (idx < outBuffer.capacity()) {
                                val v = outBuffer.get(idx)
                                if (v > maxVal) {
                                    maxVal = v
                                    maxIdx = c
                                }
                            }
                        }
                        if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= alphabet.length) {
                            recognizedChars.append(alphabet[maxIdx - 1])
                        }
                        lastIdx = maxIdx
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
