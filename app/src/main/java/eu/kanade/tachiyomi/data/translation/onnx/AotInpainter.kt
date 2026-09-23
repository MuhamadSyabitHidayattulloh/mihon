package eu.kanade.tachiyomi.data.translation.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.nio.FloatBuffer

class AotInpainter(
    private val modelFile: File,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    init {
        if (modelFile.exists()) {
            session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        }
    }

    fun inpaint(bitmap: Bitmap, textBoxes: List<RectF>): Bitmap {
        val sess = session
        if (sess == null || textBoxes.isEmpty()) {
            // Fallback: draw white mask over text boxes if model is missing/fails
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val paint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            for (box in textBoxes) {
                canvas.drawRect(box, paint)
            }
            return result
        }

        val size = 512
        val scaledImg = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        val maskPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val scaleX = size.toFloat() / bitmap.width
        val scaleY = size.toFloat() / bitmap.height

        for (box in textBoxes) {
            val scaledRect = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY,
            )
            maskCanvas.drawRect(scaledRect, maskPaint)
        }

        val imgBuffer = FloatBuffer.allocate(1 * 3 * size * size)
        val maskBuffer = FloatBuffer.allocate(1 * 1 * size * size)

        val imgPixels = IntArray(size * size)
        val maskPixels = IntArray(size * size)

        scaledImg.getPixels(imgPixels, 0, size, 0, 0, size, size)
        maskBitmap.getPixels(maskPixels, 0, size, 0, 0, size, size)

        for (c in 0..2) {
            for (i in 0 until size * size) {
                val p = imgPixels[i]
                val v = when (c) {
                    0 -> (p shr 16 and 0xFF) / 255.0f
                    1 -> (p shr 8 and 0xFF) / 255.0f
                    else -> (p and 0xFF) / 255.0f
                }
                imgBuffer.put(v)
            }
        }
        imgBuffer.rewind()

        for (i in 0 until size * size) {
            val p = maskPixels[i]
            val v = if ((p and 0xFF) > 128) 1.0f else 0.0f
            maskBuffer.put(v)
        }
        maskBuffer.rewind()

        val imgTensor = OnnxTensor.createTensor(env, imgBuffer, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val maskTensor = OnnxTensor.createTensor(env, maskBuffer, longArrayOf(1, 1, size.toLong(), size.toLong()))

        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val outputCanvas = Canvas(outputBitmap)

        try {
            val inputs = mapOf(
                sess.inputNames.elementAt(0) to imgTensor,
                sess.inputNames.elementAt(1) to maskTensor,
            )
            sess.run(inputs).use { result ->
                val outputValue = result.get(0)
                val floatBuffer = (outputValue as OnnxTensor).floatBuffer
                val channelSize = size * size
                val outPixels = IntArray(channelSize)
                val rArr = FloatArray(channelSize)
                val gArr = FloatArray(channelSize)
                val bArr = FloatArray(channelSize)

                floatBuffer.get(rArr)
                floatBuffer.get(gArr)
                floatBuffer.get(bArr)

                for (i in 0 until channelSize) {
                    val r = (rArr[i].coerceIn(0f, 1f) * 255).toInt()
                    val g = (gArr[i].coerceIn(0f, 1f) * 255).toInt()
                    val b = (bArr[i].coerceIn(0f, 1f) * 255).toInt()
                    outPixels[i] = Color.rgb(r, g, b)
                }
                val inpaintedScaled = Bitmap.createBitmap(outPixels, size, size, Bitmap.Config.ARGB_8888)
                val inpaintedFull = Bitmap.createScaledBitmap(inpaintedScaled, bitmap.width, bitmap.height, true)
                outputCanvas.drawBitmap(inpaintedFull, 0f, 0f, null)
            }
        } catch (e: Exception) {
            // Fallback on error
            val paint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            for (box in textBoxes) {
                outputCanvas.drawRect(box, paint)
            }
        } finally {
            imgTensor.close()
            maskTensor.close()
        }

        return outputBitmap
    }

    fun close() {
        session?.close()
    }
}
