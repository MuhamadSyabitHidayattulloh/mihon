package eu.kanade.tachiyomi.data.translation.pipeline

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

class ImageCleaner {

    fun clean(
        bitmap: Bitmap,
        textBlocks: List<TextBlock>,
        modelFile: File?,
    ): Bitmap {
        if (textBlocks.isEmpty()) return bitmap

        if (modelFile != null && modelFile.exists() && modelFile.length() > 0) {
            try {
                return cleanWithOnnx(bitmap, textBlocks, modelFile)
            } catch (_: Exception) {
                // Fall back if ONNX inpainting fails
            }
        }
        return cleanWithInpainting(bitmap, textBlocks)
    }

    private fun cleanWithOnnx(
        bitmap: Bitmap,
        textBlocks: List<TextBlock>,
        modelFile: File,
    ): Bitmap {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions()
        val session = env.createSession(modelFile.absolutePath, sessionOptions)

        try {
            val width = bitmap.width
            val height = bitmap.height

            val floatBuffer = FloatBuffer.allocate(1 * 3 * height * width)
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            for (c in 0 until 3) {
                for (i in 0 until height * width) {
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
                longArrayOf(1, 3, height.toLong(), width.toLong()),
            )

            val results = session.run(mapOf(session.inputNames.first() to inputTensor))
            var inpaintedBitmap: Bitmap? = null

            if (results.size() > 0) {
                val outputValue = results.get(0)
                if (outputValue is OnnxTensor) {
                    val outBuffer = outputValue.floatBuffer
                    val pixelCount = width * height
                    val outPixels = IntArray(pixelCount)

                    for (i in 0 until pixelCount) {
                        if (i < outBuffer.capacity()) {
                            val r = (outBuffer.get(i) * 255.0f).coerceIn(0f, 255f).toInt()
                            val g = (outBuffer.get((pixelCount + i).coerceAtMost(outBuffer.capacity() - 1)) * 255.0f)
                                .coerceIn(0f, 255f).toInt()
                            val b = (
                                outBuffer.get(
                                    (2 * pixelCount + i).coerceAtMost(outBuffer.capacity() - 1),
                                ) * 255.0f
                                )
                                .coerceIn(0f, 255f).toInt()
                            outPixels[i] = Color.rgb(r, g, b)
                        } else {
                            outPixels[i] = pixels[i]
                        }
                    }

                    inpaintedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    inpaintedBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
                }
            }

            inputTensor.close()
            results.close()

            return inpaintedBitmap ?: cleanWithInpainting(bitmap, textBlocks)
        } finally {
            session.close()
        }
    }

    private fun cleanWithInpainting(
        bitmap: Bitmap,
        textBlocks: List<TextBlock>,
    ): Bitmap {
        val cleanedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(cleanedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (block in textBlocks) {
            val box = block.boundingBox
            val bgBgColor = calculateBorderMedianColor(cleanedBitmap, box)
            paint.color = bgBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(box, paint)
        }
        return cleanedBitmap
    }

    private fun calculateBorderMedianColor(bitmap: Bitmap, box: RectF): Int {
        val left = box.left.coerceIn(0f, bitmap.width - 1f).toInt()
        val top = box.top.coerceIn(0f, bitmap.height - 1f).toInt()
        val right = box.right.coerceIn(left + 1f, bitmap.width.toFloat()).toInt()
        val bottom = box.bottom.coerceIn(top + 1f, bitmap.height.toFloat()).toInt()

        val samplePixels = mutableListOf<Int>()
        for (x in left until right step ((right - left) / 10).coerceAtLeast(1)) {
            samplePixels.add(bitmap.getPixel(x, top))
            samplePixels.add(bitmap.getPixel(x, (bottom - 1).coerceAtLeast(0)))
        }
        for (y in top until bottom step ((bottom - top) / 10).coerceAtLeast(1)) {
            samplePixels.add(bitmap.getPixel(left, y))
            samplePixels.add(bitmap.getPixel((right - 1).coerceAtLeast(0), y))
        }

        if (samplePixels.isEmpty()) return Color.WHITE

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        for (pixel in samplePixels) {
            rSum += Color.red(pixel)
            gSum += Color.green(pixel)
            bSum += Color.blue(pixel)
        }
        val count = samplePixels.size
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }
}
