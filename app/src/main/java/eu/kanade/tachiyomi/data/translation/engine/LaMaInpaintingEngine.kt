package eu.kanade.tachiyomi.data.translation.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.FloatBuffer

class LaMaInpaintingEngine(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var lamaSession: OrtSession? = null

    suspend fun initEngine(modelFile: File) = withContext(Dispatchers.IO) {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            lamaSession = ortEnv?.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize LaMa Inpainting ONNX session" }
        }
    }

    suspend fun inpaint(imageBitmap: Bitmap, textBoxes: List<Rect>): Bitmap = withContext(Dispatchers.IO) {
        val resultBitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        if (lamaSession != null && ortEnv != null) {
            try {
                val width = 512
                val height = 512
                val resizedImage = Bitmap.createScaledBitmap(imageBitmap, width, height, true)

                val imageBuffer = FloatBuffer.allocate(1 * 3 * height * width)
                val maskBuffer = FloatBuffer.allocate(1 * 1 * height * width)

                val pixels = IntArray(width * height)
                resizedImage.getPixels(pixels, 0, width, 0, 0, width, height)

                // Populate RGB image tensor
                for (c in 0 until 3) {
                    for (i in pixels.indices) {
                        val p = pixels[i]
                        val v = when (c) {
                            0 -> (p shr 16 and 0xFF) / 255.0f
                            1 -> (p shr 8 and 0xFF) / 255.0f
                            else -> (p and 0xFF) / 255.0f
                        }
                        imageBuffer.put(v)
                    }
                }
                imageBuffer.rewind()

                // Create mask tensor where text boxes are marked 1.0f
                val maskPixels = FloatArray(width * height) { 0.0f }
                val scaleX = width.toFloat() / imageBitmap.width
                val scaleY = height.toFloat() / imageBitmap.height

                for (box in textBoxes) {
                    val left = (box.left * scaleX).toInt().coerceIn(0, width - 1)
                    val right = (box.right * scaleX).toInt().coerceIn(0, width - 1)
                    val top = (box.top * scaleY).toInt().coerceIn(0, height - 1)
                    val bottom = (box.bottom * scaleY).toInt().coerceIn(0, height - 1)

                    for (y in top..bottom) {
                        for (x in left..right) {
                            maskPixels[y * width + x] = 1.0f
                        }
                    }
                }
                maskBuffer.put(maskPixels)
                maskBuffer.rewind()

                val imgTensor = OnnxTensor.createTensor(
                    ortEnv,
                    imageBuffer,
                    longArrayOf(1, 3, height.toLong(), width.toLong()),
                )
                val maskTensor = OnnxTensor.createTensor(
                    ortEnv,
                    maskBuffer,
                    longArrayOf(1, 1, height.toLong(), width.toLong()),
                )

                val outputs = lamaSession?.run(mapOf("image" to imgTensor, "mask" to maskTensor))
                outputs?.close()
                imgTensor.close()
                maskTensor.close()
                resizedImage.recycle()
                return@withContext resultBitmap
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "LaMa ONNX inpainting error" }
            }
        }

        // Fallback adaptive soft inpainting if LaMa ONNX model file is uninitialized
        for (box in textBoxes) {
            val bgColor = sampleTextEdgeColor(resultBitmap, box)
            val erasePaint = Paint().apply {
                color = bgColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat(),
                8f,
                8f,
                erasePaint,
            )
        }

        resultBitmap
    }

    private fun sampleTextEdgeColor(bitmap: Bitmap, box: Rect): Int {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val left = box.left.coerceIn(0, bitmap.width - 1)
        val right = box.right.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val bottom = box.bottom.coerceIn(0, bitmap.height - 1)

        val step = 2
        for (x in left..right step step) {
            val pixelTop = bitmap.getPixel(x, top)
            rSum += Color.red(pixelTop)
            gSum += Color.green(pixelTop)
            bSum += Color.blue(pixelTop)
            count++

            val pixelBottom = bitmap.getPixel(x, bottom)
            rSum += Color.red(pixelBottom)
            gSum += Color.green(pixelBottom)
            bSum += Color.blue(pixelBottom)
            count++
        }

        for (y in top..bottom step step) {
            val pixelLeft = bitmap.getPixel(left, y)
            rSum += Color.red(pixelLeft)
            gSum += Color.green(pixelLeft)
            bSum += Color.blue(pixelLeft)
            count++

            val pixelRight = bitmap.getPixel(right, y)
            rSum += Color.red(pixelRight)
            gSum += Color.green(pixelRight)
            bSum += Color.blue(pixelRight)
            count++
        }

        return if (count > 0) {
            Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
        } else {
            Color.WHITE
        }
    }

    fun close() {
        lamaSession?.close()
        ortEnv?.close()
    }
}
