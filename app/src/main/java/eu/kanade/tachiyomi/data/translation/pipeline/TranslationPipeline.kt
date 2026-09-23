package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import eu.kanade.tachiyomi.data.translation.TranslationModelManager
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngine
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class TranslationPipeline(
    private val modelManager: TranslationModelManager,
) {
    data class TextRegion(
        val rect: RectF,
        var text: String = "",
        var translatedText: String = "",
    )

    private val ortEnv = OrtEnvironment.getEnvironment()

    // PP-OCR character dictionary mapping index to character
    private val ocrDict = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ "

    suspend fun processImage(
        bitmap: Bitmap,
        engine: TranslationEngine,
        fromLang: String,
        toLang: String,
    ): Bitmap {
        val detectorFile = modelManager.getModelFile(TranslationModelManager.ModelType.DETECTOR)
        val ocrFile = modelManager.getModelFile(TranslationModelManager.ModelType.OCR)
        val inpaintingFile = modelManager.getModelFile(TranslationModelManager.ModelType.INPAINTING)

        val detectorSession = if (detectorFile.exists()) ortEnv.createSession(detectorFile.absolutePath) else null
        val ocrSession = if (ocrFile.exists()) ortEnv.createSession(ocrFile.absolutePath) else null
        val inpaintingSession = if (inpaintingFile.exists()) ortEnv.createSession(inpaintingFile.absolutePath) else null

        try {
            // 1. Text & Bubble Detection
            val regions = detectTextRegions(bitmap, detectorSession)

            if (regions.isEmpty()) {
                return bitmap
            }

            // 2. OCR on detected regions
            performOcr(bitmap, regions, ocrSession)

            // 3. Translation
            val textsToTranslate = regions.map { it.text }
            val translatedTexts = engine.translate(textsToTranslate, fromLang, toLang)
            for (i in regions.indices) {
                if (i < translatedTexts.size) {
                    regions[i].translatedText = translatedTexts[i]
                }
            }

            // 4. Inpainting / Cleaning via AOT model or masking
            val cleanedBitmap = performInpainting(bitmap, regions, inpaintingSession)

            // 5. Redrawing Canvas with Translated Text
            return redrawTextOnImage(cleanedBitmap, regions)
        } finally {
            detectorSession?.close()
            ocrSession?.close()
            inpaintingSession?.close()
        }
    }

    private fun detectTextRegions(bitmap: Bitmap, session: OrtSession?): List<TextRegion> {
        val width = bitmap.width
        val height = bitmap.height

        if (session == null) {
            return emptyList()
        }

        val inputSize = 640
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val floatBuffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (c in 0..2) {
            for (i in pixels.indices) {
                val p = pixels[i]
                val channelValue = when (c) {
                    0 -> (p shr 16 and 0xFF) / 255.0f
                    1 -> (p shr 8 and 0xFF) / 255.0f
                    else -> (p and 0xFF) / 255.0f
                }
                floatBuffer.put(channelValue)
            }
        }
        floatBuffer.rewind()

        val tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        val results = session.run(mapOf(session.inputNames.iterator().next() to tensor))

        val regions = mutableListOf<TextRegion>()
        try {
            val outputTensor = results[0] as OnnxTensor
            val outputArray = outputTensor.floatBuffer

            val numBoxes = outputTensor.info.shape.let { shape ->
                if (shape.size >= 2) shape[shape.size - 2].toInt() else 100
            }

            val scaleX = width.toFloat() / inputSize
            val scaleY = height.toFloat() / inputSize

            for (i in 0 until min(numBoxes, 30)) {
                if (outputArray.remaining() >= 6) {
                    val cx = outputArray.get() * scaleX
                    val cy = outputArray.get() * scaleY
                    val w = outputArray.get() * scaleX
                    val h = outputArray.get() * scaleY
                    val conf = outputArray.get()
                    val cls = outputArray.get()

                    if (conf > 0.30f && w > 10 && h > 10) {
                        val left = max(0f, cx - w / 2f)
                        val top = max(0f, cy - h / 2f)
                        val right = min(width.toFloat(), cx + w / 2f)
                        val bottom = min(height.toFloat(), cy + h / 2f)
                        regions.add(TextRegion(RectF(left, top, right, bottom)))
                    }
                }
            }
        } catch (e: Exception) {
            // Error handling
        } finally {
            results.close()
            tensor.close()
        }

        return regions
    }

    private fun performOcr(bitmap: Bitmap, regions: List<TextRegion>, session: OrtSession?) {
        if (session == null) return

        for (region in regions) {
            val rect = region.rect
            val cropX = max(0, rect.left.toInt())
            val cropY = max(0, rect.top.toInt())
            val cropW = min(bitmap.width - cropX, max(1, rect.width().toInt()))
            val cropH = min(bitmap.height - cropY, max(1, rect.height().toInt()))

            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            val targetH = 48
            val targetW = max(48, (cropW * (48.0f / cropH)).toInt())
            val resized = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)

            val floatBuffer = FloatBuffer.allocate(1 * 3 * targetH * targetW)
            val pixels = IntArray(targetW * targetH)
            resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

            for (c in 0..2) {
                for (p in pixels) {
                    val channelVal = when (c) {
                        0 -> ((p shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f
                        1 -> ((p shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f
                        else -> ((p and 0xFF) / 255.0f - 0.5f) / 0.5f
                    }
                    floatBuffer.put(channelVal)
                }
            }
            floatBuffer.rewind()

            val tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))
            try {
                val results = session.run(mapOf(session.inputNames.iterator().next() to tensor))
                val outputTensor = results[0] as OnnxTensor
                val floatArray = outputTensor.floatBuffer

                val shape = outputTensor.info.shape
                val seqLen = if (shape.size >= 2) shape[1].toInt() else 1
                val numClasses = if (shape.size >= 3) shape[2].toInt() else 66

                val sb = StringBuilder()
                var lastIdx = -1

                for (t in 0 until seqLen) {
                    var maxIdx = 0
                    var maxVal = -Float.MAX_VALUE
                    for (c in 0 until numClasses) {
                        if (floatArray.hasRemaining()) {
                            val valScore = floatArray.get()
                            if (valScore > maxVal) {
                                maxVal = valScore
                                maxIdx = c
                            }
                        }
                    }
                    if (maxIdx != 0 && maxIdx != lastIdx && maxIdx <= ocrDict.length) {
                        sb.append(ocrDict[maxIdx - 1])
                    }
                    lastIdx = maxIdx
                }

                region.text = sb.toString()
                results.close()
            } catch (e: Exception) {
                region.text = ""
            } finally {
                tensor.close()
            }
        }
    }

    private fun performInpainting(bitmap: Bitmap, regions: List<TextRegion>, session: OrtSession?): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        if (session != null) {
            try {
                // Prepare 512x512 image & mask tensor for AOT-Inpainting ONNX model
                val size = 512
                val imgResized = Bitmap.createScaledBitmap(bitmap, size, size, true)

                val imgBuffer = FloatBuffer.allocate(1 * 3 * size * size)
                val maskBuffer = FloatBuffer.allocate(1 * 1 * size * size)

                val pixels = IntArray(size * size)
                imgResized.getPixels(pixels, 0, size, 0, 0, size, size)

                // Fill mask array for regions
                val maskArray = FloatArray(size * size)
                val scaleX = size.toFloat() / bitmap.width
                val scaleY = size.toFloat() / bitmap.height

                for (region in regions) {
                    val r = region.rect
                    val l = (r.left * scaleX).toInt().coerceIn(0, size - 1)
                    val t = (r.top * scaleY).toInt().coerceIn(0, size - 1)
                    val right = (r.right * scaleX).toInt().coerceIn(0, size - 1)
                    val b = (r.bottom * scaleY).toInt().coerceIn(0, size - 1)

                    for (y in t..b) {
                        for (x in l..right) {
                            maskArray[y * size + x] = 1.0f
                        }
                    }
                }

                for (c in 0..2) {
                    for (i in pixels.indices) {
                        val p = pixels[i]
                        val v = when (c) {
                            0 -> (p shr 16 and 0xFF) / 255.0f
                            1 -> (p shr 8 and 0xFF) / 255.0f
                            else -> (p and 0xFF) / 255.0f
                        }
                        imgBuffer.put(v)
                    }
                }
                imgBuffer.rewind()

                for (v in maskArray) {
                    maskBuffer.put(v)
                }
                maskBuffer.rewind()

                val imgTensor = OnnxTensor.createTensor(ortEnv, imgBuffer, longArrayOf(1, 3, size.toLong(), size.toLong()))
                val maskTensor = OnnxTensor.createTensor(ortEnv, maskBuffer, longArrayOf(1, 1, size.toLong(), size.toLong()))

                val inputs = mapOf(
                    session.inputNames.elementAt(0) to imgTensor,
                    session.inputNames.elementAt(1) to maskTensor,
                )

                val results = session.run(inputs)
                val outTensor = results[0] as OnnxTensor
                val outBuffer = outTensor.floatBuffer

                val inpaintedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val outPixels = IntArray(size * size)

                for (i in 0 until size * size) {
                    val r = (outBuffer.get(i) * 255).toInt().coerceIn(0, 255)
                    val g = (outBuffer.get(size * size + i) * 255).toInt().coerceIn(0, 255)
                    val b = (outBuffer.get(2 * size * size + i) * 255).toInt().coerceIn(0, 255)
                    outPixels[i] = Color.rgb(r, g, b)
                }
                inpaintedBitmap.setPixels(outPixels, 0, size, 0, 0, size, size)

                results.close()
                imgTensor.close()
                maskTensor.close()

                return Bitmap.createScaledBitmap(inpaintedBitmap, bitmap.width, bitmap.height, true)
            } catch (e: Exception) {
                // Fallback white fill if model execution fails
            }
        }

        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        for (region in regions) {
            val rect = region.rect
            val padding = 4f
            val paddedRect = RectF(
                max(0f, rect.left - padding),
                max(0f, rect.top - padding),
                min(bitmap.width.toFloat(), rect.right + padding),
                min(bitmap.height.toFloat(), rect.bottom + padding),
            )
            canvas.drawRoundRect(paddedRect, 8f, 8f, paint)
        }

        return resultBitmap
    }

    private fun redrawTextOnImage(bitmap: Bitmap, regions: List<TextRegion>): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (region in regions) {
            val text = region.translatedText.ifBlank { region.text }
            if (text.isBlank()) continue

            val rect = region.rect
            val maxWidth = rect.width() * 0.9f
            val maxHeight = rect.height() * 0.9f

            var textSize = min(maxHeight / 2f, 32f)
            textPaint.textSize = textSize

            while (textSize > 10f) {
                textPaint.textSize = textSize
                val measuredWidth = textPaint.measureText(text)
                if (measuredWidth <= maxWidth) break
                textSize -= 2f
            }

            val centerX = rect.centerX()
            val centerY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)

            canvas.drawText(text, centerX, centerY, textPaint)
        }

        return resultBitmap
    }
}
