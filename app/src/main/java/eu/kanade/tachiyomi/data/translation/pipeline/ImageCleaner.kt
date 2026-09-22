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
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageCleaner {

    fun clean(
        bitmap: Bitmap,
        textBlocks: List<TextBlock>,
        modelFile: File?,
        session: OrtSession? = null,
    ): Bitmap {
        if (textBlocks.isEmpty()) return bitmap

        if (session != null) {
            try {
                return cleanWithSession(bitmap, textBlocks, session)
            } catch (_: Throwable) {
                // Fall back if ONNX inpainting fails
            }
        } else if (modelFile != null && modelFile.exists() && modelFile.length() > 0) {
            try {
                val env = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions()
                env.createSession(modelFile.absolutePath, sessionOptions).use { newSession ->
                    return cleanWithSession(bitmap, textBlocks, newSession)
                }
            } catch (_: Throwable) {
                // Fall back if ONNX inpainting fails
            }
        }
        return cleanWithInpainting(bitmap, textBlocks)
    }

    private fun cleanWithSession(
        bitmap: Bitmap,
        textBlocks: List<TextBlock>,
        session: OrtSession,
    ): Bitmap {
        val env = OrtEnvironment.getEnvironment()
        val targetDim = 512
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetDim, targetDim, true)

        val byteBuffer = ByteBuffer.allocateDirect(1 * 3 * targetDim * targetDim * 4)
            .order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val pixels = IntArray(targetDim * targetDim)
        scaledBitmap.getPixels(pixels, 0, targetDim, 0, 0, targetDim, targetDim)

        for (c in 0 until 3) {
            for (i in 0 until targetDim * targetDim) {
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
            longArrayOf(1, 3, targetDim.toLong(), targetDim.toLong()),
        )

        var inpaintedBitmap: Bitmap? = null
        try {
            val inputName = session.inputNames.firstOrNull() ?: return cleanWithInpainting(bitmap, textBlocks)
            val results = session.run(mapOf(inputName to inputTensor))
            results.use { res ->
                if (res.size() > 0) {
                    val outputValue = res.get(0)
                    if (outputValue is OnnxTensor) {
                        val outBuffer = outputValue.floatBuffer
                        val pixelCount = targetDim * targetDim
                        val outPixels = IntArray(pixelCount)

                        for (i in 0 until pixelCount) {
                            if (i < outBuffer.capacity()) {
                                val r = (outBuffer.get(i) * 255.0f).coerceIn(0f, 255f).toInt()
                                val g = (
                                    outBuffer.get(
                                        (pixelCount + i).coerceAtMost(outBuffer.capacity() - 1),
                                    ) * 255.0f
                                    )
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

                        val tempInpainted = Bitmap.createBitmap(targetDim, targetDim, Bitmap.Config.ARGB_8888)
                        tempInpainted.setPixels(outPixels, 0, targetDim, 0, 0, targetDim, targetDim)
                        inpaintedBitmap = Bitmap.createScaledBitmap(tempInpainted, bitmap.width, bitmap.height, true)
                    }
                }
            }
        } finally {
            inputTensor.close()
        }

        return inpaintedBitmap ?: cleanWithInpainting(bitmap, textBlocks)
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
