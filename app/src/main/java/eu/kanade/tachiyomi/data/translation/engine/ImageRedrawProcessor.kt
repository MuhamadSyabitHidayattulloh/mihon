package eu.kanade.tachiyomi.data.translation.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.service.TranslationPreferences

class ImageRedrawProcessor(
    private val context: Context,
    private val engineManager: TranslationEngineManager,
    private val preferences: TranslationPreferences,
) {

    private val paddleEngine = PaddleOcrEngine(context)

    suspend fun processAndRedraw(inputBitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        // Exclusively use PaddleOCR ONNX Engine for detection and recognition
        val rawOcrBlocks = paddleEngine.processImage(inputBitmap)
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

            // Stage 4: Advanced Adaptive Background Inpainting
            val bgColor = sampleTextEdgeColor(resultBitmap, box)

            // Soft rounded inpainting mask to blend cleanly with speech bubble / background
            val erasePaint = Paint().apply {
                color = bgColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val cornerRadius = 8f
            canvas.drawRoundRect(RectF(box), cornerRadius, cornerRadius, erasePaint)

            // Stage 5: High Contrast Typesetting
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
