package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

class CanvasRenderer {
    /**
     * Draws translated text onto cleaned bitmap regions using the selected comic font.
     */
    fun renderTranslatedText(
        cleanedBitmap: Bitmap,
        regions: List<TextRegion>,
        fontName: String,
    ): Bitmap {
        val resultBitmap = cleanedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val typeface = when (fontName.lowercase()) {
            "anime ace", "animeace" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            "manga temple", "mangatemple" -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            "wild words", "wildwords" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
            else -> Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        for (region in regions) {
            val text = region.translatedText
            if (text.isBlank()) continue

            val rect = region.boundingBox
            val width = rect.width().coerceAtLeast(10)
            val height = rect.height().coerceAtLeast(10)

            val textPaint = TextPaint().apply {
                isAntiAlias = true
                color = Color.BLACK
                this.typeface = typeface
                textAlign = Paint.Align.CENTER
            }

            // Calculate optimal text size to fit box
            var textSize = (height / 4f).coerceIn(12f, 48f)
            textPaint.textSize = textSize

            var layout = createLayout(text, textPaint, width)
            while (layout.height > height && textSize > 10f) {
                textSize -= 2f
                textPaint.textSize = textSize
                layout = createLayout(text, textPaint, width)
            }

            canvas.save()
            val startX = rect.left + width / 2f
            val startY = rect.top + (height - layout.height) / 2f
            canvas.translate(startX, startY)
            layout.draw(canvas)
            canvas.restore()
        }

        return resultBitmap
    }

    private fun createLayout(text: String, textPaint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    }
}
