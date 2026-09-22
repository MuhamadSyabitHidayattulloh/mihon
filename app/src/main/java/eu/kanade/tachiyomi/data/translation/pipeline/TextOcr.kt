package eu.kanade.tachiyomi.data.translation.pipeline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TextOcr {

    private val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!?,.:;-'\" "

    fun recognize(
        bitmap: Bitmap,
        detectedBlocks: List<DetectedBlock>,
        modelFile: File?,
        session: OrtSession? = null,
    ): List<TextBlock> {
        if (detectedBlocks.isEmpty()) return emptyList()

        if (session != null) {
            try {
                return recognizeWithSession(bitmap, detectedBlocks, session)
            } catch (_: Throwable) {
                // Fall back if ONNX OCR fails
            }
        } else if (modelFile != null && modelFile.exists() && modelFile.length() > 0) {
            try {
                val env = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions()
                env.createSession(modelFile.absolutePath, sessionOptions).use { newSession ->
                    return recognizeWithSession(bitmap, detectedBlocks, newSession)
                }
            } catch (_: Throwable) {
                // Fall back if ONNX OCR fails
            }
        }
        return recognizeWithFallback(bitmap, detectedBlocks)
    }

    private fun recognizeWithSession(
        bitmap: Bitmap,
        detectedBlocks: List<DetectedBlock>,
        session: OrtSession,
    ): List<TextBlock> {
        val env = OrtEnvironment.getEnvironment()
        val resultBlocks = mutableListOf<TextBlock>()

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

            val byteBuffer = ByteBuffer.allocateDirect(1 * 3 * targetHeight * targetWidth * 4)
                .order(ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()

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

            val recognizedChars = StringBuilder()
            try {
                val inputName = session.inputNames.firstOrNull() ?: continue
                val results = session.run(mapOf(inputName to inputTensor))
                results.use { res ->
                    if (res.size() > 0) {
                        val outputValue = res.get(0)
                        if (outputValue is OnnxTensor) {
                            val outFloatBuffer = outputValue.floatBuffer
                            val totalCapacity = outFloatBuffer.capacity()

                            val numClasses = alphabet.length + 1
                            val numSteps = totalCapacity / numClasses

                            var prevClassIdx = -1
                            if (numSteps > 0 && numClasses > 1) {
                                for (step in 0 until numSteps) {
                                    var maxIdx = 0
                                    var maxVal = Float.NEGATIVE_INFINITY
                                    for (c in 0 until numClasses) {
                                        val valAt = outFloatBuffer.get(step * numClasses + c)
                                        if (valAt > maxVal) {
                                            maxVal = valAt
                                            maxIdx = c
                                        }
                                    }
                                    // CTC Decoding logic: 0 is blank class, merge repeating tokens
                                    if (maxIdx != 0 && maxIdx != prevClassIdx) {
                                        if (maxIdx - 1 in alphabet.indices) {
                                            recognizedChars.append(alphabet[maxIdx - 1])
                                        }
                                    }
                                    prevClassIdx = maxIdx
                                }
                            }
                        }
                    }
                }
            } finally {
                inputTensor.close()
            }

            val text = recognizedChars.toString().trim().ifBlank { "..." }
            resultBlocks.add(TextBlock(boundingBox = RectF(box), originalText = text))
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
