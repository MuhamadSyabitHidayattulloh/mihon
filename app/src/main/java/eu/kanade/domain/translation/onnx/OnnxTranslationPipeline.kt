package eu.kanade.domain.translation.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.translation.engine.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.nio.FloatBuffer

@Inject
@SingleIn(AppScope::class)
class OnnxTranslationPipeline(
    private val modelDownloader: TranslationModelDownloader,
) {
    private var ortEnv: OrtEnvironment? = null
    private var detectorSession: OrtSession? = null
    private var ocrSession: OrtSession? = null
    private var inpaintingSession: OrtSession? = null

    @Synchronized
    private fun initSessions() {
        if (ortEnv == null) {
            try {
                ortEnv = OrtEnvironment.getEnvironment()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to init OrtEnvironment" }
            }
        }
        val env = ortEnv ?: return

        if (detectorSession == null && modelDownloader.detectorFile.exists() &&
            modelDownloader.detectorFile.length() > 0L
        ) {
            try {
                detectorSession =
                    env.createSession(modelDownloader.detectorFile.absolutePath, OrtSession.SessionOptions())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create detector session" }
            }
        }

        if (ocrSession == null && modelDownloader.ocrFile.exists() && modelDownloader.ocrFile.length() > 0L) {
            try {
                ocrSession = env.createSession(modelDownloader.ocrFile.absolutePath, OrtSession.SessionOptions())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create OCR session" }
            }
        }

        if (inpaintingSession == null && modelDownloader.inpaintingFile.exists() &&
            modelDownloader.inpaintingFile.length() > 0L
        ) {
            try {
                inpaintingSession =
                    env.createSession(modelDownloader.inpaintingFile.absolutePath, OrtSession.SessionOptions())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to create Inpainting session" }
            }
        }
    }

    suspend fun processPage(
        originalBitmap: Bitmap,
        engine: TranslationEngine,
        fromLang: String,
        toLang: String,
        onStageChanged: (String) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.IO) {
        initSessions()

        // 1. Detect Bubbles
        onStageChanged("DETECTION")
        val boxes = detectBubbles(originalBitmap)

        if (boxes.isEmpty()) {
            return@withContext originalBitmap
        }

        // 2. OCR
        onStageChanged("OCR")
        val extractedTexts = ocrBoxes(originalBitmap, boxes)

        // 3. Translation
        onStageChanged("TRANSLATION")
        val translatedTexts = engine.translate(extractedTexts, fromLang, toLang)

        // 4. Cleaning / Inpainting
        onStageChanged("CLEANING")
        val cleanBitmap = cleanImage(originalBitmap, boxes)

        // 5. Redraw Text / Rendering
        onStageChanged("RENDERING")
        val renderedBitmap = renderText(cleanBitmap, boxes, translatedTexts)

        renderedBitmap
    }

    private fun detectBubbles(bitmap: Bitmap): List<RectF> {
        val session = detectorSession
        val env = ortEnv
        if (session != null && env != null) {
            try {
                val width = bitmap.width
                val height = bitmap.height
                val targetSize = 640
                val scaled = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)

                val inputBuffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)
                val pixels = IntArray(targetSize * targetSize)
                scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

                for (i in pixels.indices) {
                    val p = pixels[i]
                    inputBuffer.put(i, ((p shr 16 and 0xFF) / 255.0f))
                    inputBuffer.put(targetSize * targetSize + i, ((p shr 8 and 0xFF) / 255.0f))
                    inputBuffer.put(2 * targetSize * targetSize + i, ((p and 0xFF) / 255.0f))
                }

                val tensor = OnnxTensor.createTensor(
                    env,
                    inputBuffer,
                    longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong()),
                )
                session.run(mapOf("images" to tensor)).use { result ->
                    val outputTensor = result[0]
                    if (outputTensor is OnnxTensor) {
                        val floatBuffer = outputTensor.floatBuffer
                        val shape = outputTensor.info.shape
                        val boxes = mutableListOf<RectF>()
                        val scaleX = width.toFloat() / targetSize
                        val scaleY = height.toFloat() / targetSize

                        if (shape.size >= 2) {
                            val numDetections = shape[shape.size - 2].toInt()
                            val numFields = shape[shape.size - 1].toInt()
                            for (i in 0 until numDetections) {
                                val offset = i * numFields
                                if (offset + 4 < floatBuffer.capacity()) {
                                    val cx = floatBuffer.get(offset) * scaleX
                                    val cy = floatBuffer.get(offset + 1) * scaleY
                                    val w = floatBuffer.get(offset + 2) * scaleX
                                    val h = floatBuffer.get(offset + 3) * scaleY
                                    val conf = if (numFields >= 5) floatBuffer.get(offset + 4) else 1.0f

                                    if (conf > 0.35f && w > 20 && h > 20) {
                                        boxes.add(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
                                    }
                                }
                            }
                        }
                        if (boxes.isNotEmpty()) return mergeOverlappingBoxes(boxes)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "ONNX Detector error, falling back to heuristic detector" }
            }
        }

        return heuristicDetectBubbles(bitmap)
    }

    private fun heuristicDetectBubbles(bitmap: Bitmap): List<RectF> {
        val width = bitmap.width
        val height = bitmap.height
        val boxes = mutableListOf<RectF>()

        val scale = 0.25f
        val sw = (width * scale).toInt().coerceAtLeast(1)
        val sh = (height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, false)

        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        val isWhite = BooleanArray(sw * sh)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val brightness = (r + g + b) / 3
            isWhite[i] = brightness > 220
        }

        val visited = BooleanArray(sw * sh)
        for (y in 0 until sh step 4) {
            for (x in 0 until sw step 4) {
                val idx = y * sw + x
                if (isWhite[idx] && !visited[idx]) {
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y

                    val queue = IntArray(sw * sh)
                    var head = 0
                    var tail = 0
                    queue[tail++] = idx
                    visited[idx] = true

                    while (head < tail) {
                        val curr = queue[head++]
                        val cx = curr % sw
                        val cy = curr / sw

                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy

                        val neighbors = intArrayOf(
                            curr - 1,
                            curr + 1,
                            curr - sw,
                            curr + sw,
                        )
                        for (n in neighbors) {
                            if (n in 0 until (sw * sh) && !visited[n] && isWhite[n]) {
                                val nx = n % sw
                                val ny = n / sw
                                if (kotlin.math.abs(nx - cx) <= 1 && kotlin.math.abs(ny - cy) <= 1) {
                                    visited[n] = true
                                    queue[tail++] = n
                                }
                            }
                        }
                    }

                    val boxW = (maxX - minX + 1) / scale
                    val boxH = (maxY - minY + 1) / scale
                    if (boxW in 40.0f..0.8f * width && boxH in 25.0f..0.8f * height) {
                        val rect = RectF(
                            minX / scale,
                            minY / scale,
                            (maxX + 1) / scale,
                            (maxY + 1) / scale,
                        )
                        boxes.add(rect)
                    }
                }
            }
        }
        small.recycle()
        return mergeOverlappingBoxes(boxes)
    }

    private fun mergeOverlappingBoxes(boxes: List<RectF>): List<RectF> {
        val result = mutableListOf<RectF>()
        for (box in boxes) {
            var merged = false
            for (existing in result) {
                if (RectF.intersects(existing, box)) {
                    existing.union(box)
                    merged = true
                    break
                }
            }
            if (!merged) {
                result.add(RectF(box))
            }
        }
        return result
    }

    private fun ocrBoxes(bitmap: Bitmap, boxes: List<RectF>): List<String> {
        val session = ocrSession
        val env = ortEnv
        val results = mutableListOf<String>()

        for (box in boxes) {
            val left = box.left.toInt().coerceIn(0, bitmap.width - 1)
            val top = box.top.toInt().coerceIn(0, bitmap.height - 1)
            val right = box.right.toInt().coerceIn(left + 1, bitmap.width)
            val bottom = box.bottom.toInt().coerceIn(top + 1, bitmap.height)

            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)

            var extractedText = ""
            if (session != null && env != null) {
                try {
                    val targetH = 48
                    val targetW = (crop.width * (48.0f / crop.height)).toInt().coerceIn(48, 320)
                    val scaledCrop = Bitmap.createScaledBitmap(crop, targetW, targetH, true)

                    val buffer = FloatBuffer.allocate(1 * 3 * targetH * targetW)
                    val pixels = IntArray(targetW * targetH)
                    scaledCrop.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

                    for (i in pixels.indices) {
                        val p = pixels[i]
                        buffer.put(i, (((p shr 16 and 0xFF) / 255.0f) - 0.5f) / 0.5f)
                        buffer.put(targetW * targetH + i, (((p shr 8 and 0xFF) / 255.0f) - 0.5f) / 0.5f)
                        buffer.put(2 * targetW * targetH + i, (((p and 0xFF) / 255.0f) - 0.5f) / 0.5f)
                    }

                    val tensor = OnnxTensor.createTensor(
                        env,
                        buffer,
                        longArrayOf(1, 3, targetH.toLong(), targetW.toLong()),
                    )
                    session.run(mapOf("x" to tensor)).use { res ->
                        val outputTensor = res[0]
                        if (outputTensor is OnnxTensor) {
                            val floatBuf = outputTensor.floatBuffer
                            val shape = outputTensor.info.shape
                            if (shape.size >= 3) {
                                val timeSteps = shape[1].toInt()
                                val numClasses = shape[2].toInt()
                                val sb = StringBuilder()
                                var lastIndex = -1

                                for (t in 0 until timeSteps) {
                                    var maxIdx = 0
                                    var maxVal = Float.NEGATIVE_INFINITY
                                    for (c in 0 until numClasses) {
                                        val valAt = floatBuf.get(t * numClasses + c)
                                        if (valAt > maxVal) {
                                            maxVal = valAt
                                            maxIdx = c
                                        }
                                    }
                                    if (maxIdx != 0 && maxIdx != lastIndex) {
                                        val char = decodeOcrIndex(maxIdx)
                                        if (char != null) sb.append(char)
                                    }
                                    lastIndex = maxIdx
                                }
                                extractedText = sb.toString()
                            }
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "ONNX OCR error" }
                }
            }

            if (extractedText.isBlank()) {
                extractedText = "Speech Bubble"
            }
            crop.recycle()
            results.add(extractedText)
        }
        return results
    }

    private fun decodeOcrIndex(index: Int): Char? {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!?,.- "
        return alphabet.getOrNull(index % alphabet.length)
    }

    private fun cleanImage(bitmap: Bitmap, boxes: List<RectF>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val session = inpaintingSession
        val env = ortEnv
        if (session != null && env != null) {
            try {
                // AOT Inpainting Execution
                val w = bitmap.width
                val h = bitmap.height
                val imgBuf = FloatBuffer.allocate(1 * 3 * h * w)
                val maskBuf = FloatBuffer.allocate(1 * 1 * h * w)
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

                val isMasked = BooleanArray(w * h)
                for (box in boxes) {
                    val minX = box.left.toInt().coerceIn(0, w - 1)
                    val maxX = box.right.toInt().coerceIn(0, w - 1)
                    val minY = box.top.toInt().coerceIn(0, h - 1)
                    val maxY = box.bottom.toInt().coerceIn(0, h - 1)
                    for (y in minY..maxY) {
                        for (x in minX..maxX) {
                            isMasked[y * w + x] = true
                        }
                    }
                }

                for (i in pixels.indices) {
                    val p = pixels[i]
                    imgBuf.put(i, ((p shr 16 and 0xFF) / 255.0f))
                    imgBuf.put(h * w + i, ((p shr 8 and 0xFF) / 255.0f))
                    imgBuf.put(2 * h * w + i, ((p and 0xFF) / 255.0f))
                    maskBuf.put(i, if (isMasked[i]) 1.0f else 0.0f)
                }

                val imgTensor = OnnxTensor.createTensor(env, imgBuf, longArrayOf(1, 3, h.toLong(), w.toLong()))
                val maskTensor = OnnxTensor.createTensor(env, maskBuf, longArrayOf(1, 1, h.toLong(), w.toLong()))

                session.run(mapOf("image" to imgTensor, "mask" to maskTensor)).use { res ->
                    val outTensor = res[0]
                    if (outTensor is OnnxTensor) {
                        val outBuf = outTensor.floatBuffer
                        val cleanPixels = IntArray(w * h)
                        for (i in cleanPixels.indices) {
                            val r = (outBuf.get(i).coerceIn(0f, 1f) * 255).toInt()
                            val g = (outBuf.get(h * w + i).coerceIn(0f, 1f) * 255).toInt()
                            val b = (outBuf.get(2 * h * w + i).coerceIn(0f, 1f) * 255).toInt()
                            cleanPixels[i] = Color.rgb(r, g, b)
                        }
                        result.setPixels(cleanPixels, 0, w, 0, 0, w, h)
                        return result
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "AOT Inpainting ONNX failed, fallback to fill" }
            }
        }

        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (box in boxes) {
            val bgPixel = sampleBackgroundColor(bitmap, box)
            paint.color = bgPixel

            val padding = 4.0f
            val cleanRect = RectF(
                (box.left + padding).coerceAtMost(box.right),
                (box.top + padding).coerceAtMost(box.bottom),
                (box.right - padding).coerceAtLeast(box.left),
                (box.bottom - padding).coerceAtLeast(box.top),
            )
            canvas.drawRoundRect(cleanRect, 12.0f, 12.0f, paint)
        }

        return result
    }

    private fun sampleBackgroundColor(bitmap: Bitmap, box: RectF): Int {
        val cornerX = box.left.toInt().coerceIn(0, bitmap.width - 1)
        val cornerY = box.top.toInt().coerceIn(0, bitmap.height - 1)

        val cornerPixel = bitmap.getPixel(cornerX, cornerY)
        val r = (cornerPixel shr 16) and 0xFF
        val g = (cornerPixel shr 8) and 0xFF
        val b = cornerPixel and 0xFF
        return if ((r + g + b) / 3 > 200) cornerPixel else Color.WHITE
    }

    private fun renderText(
        cleanBitmap: Bitmap,
        boxes: List<RectF>,
        translatedTexts: List<String>,
    ): Bitmap {
        val result = cleanBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        for (i in boxes.indices) {
            val box = boxes[i]
            val text = translatedTexts.getOrNull(i) ?: continue
            if (text.isBlank()) continue

            val boxWidth = (box.width() - 8).coerceAtLeast(20.0f).toInt()
            val boxHeight = (box.height() - 8).coerceAtLeast(20.0f)

            var fontSize = (boxHeight * 0.35f).coerceIn(12.0f, 48.0f)
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = fontSize
            }

            var layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, boxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0.0f, 0.95f)
                .build()

            while (layout.height > boxHeight && fontSize > 10.0f) {
                fontSize -= 2.0f
                textPaint.textSize = fontSize
                layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, boxWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0.0f, 0.95f)
                    .build()
            }

            canvas.save()
            val startX = box.left + 4.0f
            val startY = box.top + ((box.height() - layout.height) / 2.0f).coerceAtLeast(2.0f)
            canvas.translate(startX, startY)
            layout.draw(canvas)
            canvas.restore()
        }

        return result
    }
}
