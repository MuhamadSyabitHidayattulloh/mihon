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
import java.nio.FloatBuffer

class TextDetector {

    fun detect(bitmap: Bitmap, modelFile: File?, session: OrtSession? = null): List<DetectedBlock> {
        if (session != null) {
            try {
                return detectWithSession(bitmap, session)
            } catch (_: Throwable) {
                // Fall back if ONNX inference fails
            }
        } else if (modelFile != null && modelFile.exists() && modelFile.length() > 0) {
            try {
                val env = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions()
                env.createSession(modelFile.absolutePath, sessionOptions).use { newSession ->
                    return detectWithSession(bitmap, newSession)
                }
            } catch (_: Throwable) {
                // Fall back if ONNX inference fails
            }
        }
        return detectWithFallback(bitmap)
    }

    private fun detectWithSession(bitmap: Bitmap, session: OrtSession): List<DetectedBlock> {
        val env = OrtEnvironment.getEnvironment()
        val targetWidth = 640
        val targetHeight = 640
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        val byteBuffer = ByteBuffer.allocateDirect(1 * 3 * targetHeight * targetWidth * 4)
            .order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val pixels = IntArray(targetWidth * targetHeight)
        scaledBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

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

        val detectedBlocks = mutableListOf<DetectedBlock>()
        try {
            val inputName = session.inputNames.firstOrNull() ?: return detectWithFallback(bitmap)
            val results = session.run(mapOf(inputName to inputTensor))
            results.use { res ->
                if (res.size() > 0) {
                    val outputValue = res.get(0)
                    if (outputValue is OnnxTensor) {
                        val outFloatBuffer = outputValue.floatBuffer
                        val scaleX = bitmap.width.toFloat() / targetWidth
                        val scaleY = bitmap.height.toFloat() / targetHeight

                        val elementCount = outFloatBuffer.capacity()
                        val stride = 6
                        val numBoxes = elementCount / stride

                        for (i in 0 until numBoxes) {
                            val idx = i * stride
                            if (idx + 4 < elementCount) {
                                val x1 = outFloatBuffer.get(idx) * scaleX
                                val y1 = outFloatBuffer.get(idx + 1) * scaleY
                                val x2 = outFloatBuffer.get(idx + 2) * scaleX
                                val y2 = outFloatBuffer.get(idx + 3) * scaleY
                                val conf = outFloatBuffer.get(idx + 4)

                                if (conf > 0.30f && (x2 - x1) > 10 && (y2 - y1) > 10) {
                                    detectedBlocks.add(
                                        DetectedBlock(
                                            boundingBox = RectF(x1, y1, x2, y2),
                                            confidence = conf,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            inputTensor.close()
        }

        return if (detectedBlocks.isNotEmpty()) detectedBlocks else detectWithFallback(bitmap)
    }

    private fun detectWithFallback(bitmap: Bitmap): List<DetectedBlock> {
        val blocks = mutableListOf<DetectedBlock>()
        val width = bitmap.width
        val height = bitmap.height

        val gridCols = 16
        val gridRows = 24
        val cellW = width / gridCols
        val cellH = height / gridRows

        if (cellW <= 0 || cellH <= 0) return blocks

        val whiteGrid = Array(gridRows) { BooleanArray(gridCols) }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                val startX = c * cellW
                val startY = r * cellH
                var brightPixels = 0
                val totalSample = cellW * cellH

                for (y in startY until (startY + cellH)) {
                    for (x in startX until (startX + cellW)) {
                        val px = pixels[y * width + x]
                        val brightness = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3
                        if (brightness > 220) brightPixels++
                    }
                }
                if (totalSample > 0 && (brightPixels.toFloat() / totalSample) > 0.6f) {
                    whiteGrid[r][c] = true
                }
            }
        }

        val visited = Array(gridRows) { BooleanArray(gridCols) }
        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                if (whiteGrid[r][c] && !visited[r][c]) {
                    var minR = r
                    var maxR = r
                    var minC = c
                    var maxC = c

                    val queue = mutableListOf(r to c)
                    visited[r][c] = true

                    while (queue.isNotEmpty()) {
                        val (currR, currC) = queue.removeAt(0)
                        minR = minOf(minR, currR)
                        maxR = maxOf(maxR, currR)
                        minC = minOf(minC, currC)
                        maxC = maxOf(maxC, currC)

                        val neighbors = listOf(
                            currR - 1 to currC,
                            currR + 1 to currC,
                            currR to currC - 1,
                            currR to currC + 1,
                        )
                        for (nr in neighbors) {
                            if (nr.first in 0 until gridRows && nr.second in 0 until gridCols) {
                                if (whiteGrid[nr.first][nr.second] && !visited[nr.first][nr.second]) {
                                    visited[nr.first][nr.second] = true
                                    queue.add(nr)
                                }
                            }
                        }
                    }

                    val boxW = (maxC - minC + 1) * cellW
                    val boxH = (maxR - minR + 1) * cellH
                    if (boxW >= cellW * 1.5f && boxH >= cellH * 1.5f && boxW < width * 0.9f && boxH < height * 0.9f) {
                        blocks.add(
                            DetectedBlock(
                                boundingBox = RectF(
                                    (minC * cellW).toFloat(),
                                    (minR * cellH).toFloat(),
                                    ((maxC + 1) * cellW).toFloat(),
                                    ((maxR + 1) * cellH).toFloat(),
                                ),
                                confidence = 0.85f,
                            ),
                        )
                    }
                }
            }
        }

        return blocks
    }
}
