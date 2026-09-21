package eu.kanade.tachiyomi.data.translation.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.service.TranslationPreferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImageRedrawProcessor(
    private val context: Context,
    private val engineManager: TranslationEngineManager,
    private val preferences: TranslationPreferences,
) {

    private val paddleEngine = PaddleOcrEngine(context)

    suspend fun processAndRedraw(inputBitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val rawOcrBlocks = mutableListOf<OcrResultBlock>()

        try {
            rawOcrBlocks.addAll(paddleEngine.processImage(inputBitmap))
        } catch (_: Exception) {}

        if (rawOcrBlocks.isEmpty()) {
            val image = InputImage.fromBitmap(inputBitmap, 0)
            val recognizers = listOf(
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            )

            try {
                for (recognizer in recognizers) {
                    try {
                        val visionText = suspendCancellableCoroutine { continuation ->
                            recognizer.process(image)
                                .addOnSuccessListener { text -> continuation.resume(text) }
                                .addOnFailureListener { e -> continuation.resumeWithException(e) }
                        }
                        for (block in visionText.textBlocks) {
                            val box = block.boundingBox ?: continue
                            if (block.text.isNotBlank()) {
                                rawOcrBlocks.add(OcrResultBlock(box, block.text))
                            }
                        }
                    } catch (_: Exception) {}
                }
            } finally {
                recognizers.forEach { it.close() }
            }
        }

        val sortedBlocks = sortMangaReadingOrder(rawOcrBlocks)

        val resultBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val sourceLang = preferences.sourceLanguage.get()
        val targetLang = preferences.targetLanguage.get()
        val fontName = preferences.fontSelection.get()
        val typeface = try {
            when (fontName) {
                "Anime Ace" -> Typeface.createFromAsset(context.assets, "fonts/anime_ace.ttf")
                "Manga Master BB" -> Typeface.createFromAsset(context.assets, "fonts/manga_master_bb.ttf")
                "Comic Font" -> Typeface.createFromAsset(context.assets, "fonts/comic_font.ttf")
                else -> Typeface.createFromAsset(context.assets, "fonts/anime_ace.ttf")
            }
        } catch (_: Exception) {
            Typeface.create("sans-serif", Typeface.BOLD)
        }

        for (block in sortedBlocks) {
            val box = block.box
            val originalText = block.text
            if (originalText.isBlank()) continue

            val detectedLang = if (sourceLang.isNotBlank()) sourceLang else engineManager.detectLanguage(originalText)
            val translatedText = try {
                engineManager.translate(originalText, detectedLang, targetLang)
            } catch (e: Exception) {
                originalText
            }

            // Calculate dominant background color by edge sampling around the text box
            val bgColor = sampleTextEdgeColor(resultBitmap, box)

            // Dynamic Inpainting: Fill original text box with sampled dynamic background color
            val erasePaint = Paint().apply {
                color = bgColor
                style = Paint.Style.FILL
            }
            canvas.drawRect(box, erasePaint)

            // Determine text color for high contrast against background
            val luminance =
                (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
            val textColor = if (luminance > 0.5) Color.BLACK else Color.WHITE

            val textPaint = TextPaint().apply {
                this.color = textColor
                this.typeface = typeface
                this.isAntiAlias = true
            }

            drawTextToFitBubble(canvas, translatedText, box, textPaint)
        }

        resultBitmap
    }

    private fun sortMangaReadingOrder(blocks: List<OcrResultBlock>): List<OcrResultBlock> {
        return blocks.sortedWith(
            Comparator { b1, b2 ->
                val yDiff = Math.abs(b1.box.top - b2.box.top)
                if (yDiff < 40) {
                    b2.box.right.compareTo(b1.box.right)
                } else {
                    b1.box.top.compareTo(b2.box.top)
                }
            },
        )
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

        val step = 4
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

    private fun drawTextToFitBubble(canvas: Canvas, text: String, box: Rect, textPaint: TextPaint) {
        val width = box.width().coerceAtLeast(1)
        val height = box.height().coerceAtLeast(1)

        var low = 8f
        var high = 72f
        var bestSize = low

        while (low <= high) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            if (layout.height <= height) {
                bestSize = mid
                low = mid + 0.5f
            } else {
                high = mid - 0.5f
            }
        }

        textPaint.textSize = bestSize
        val finalLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        canvas.save()
        val yOffset = box.top + ((height - finalLayout.height) / 2f).coerceAtLeast(0f)
        canvas.translate(box.left.toFloat(), yOffset)
        finalLayout.draw(canvas)
        canvas.restore()
    }
}
