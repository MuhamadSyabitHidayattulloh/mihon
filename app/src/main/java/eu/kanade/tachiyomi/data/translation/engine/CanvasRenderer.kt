package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import tachiyomi.domain.translation.service.TranslationPreferences

class CanvasRenderer(
    private val preferences: TranslationPreferences,
) {

    /**
     * Renders translated texts onto speech bubble regions on a cleaned bitmap.
     */
    fun renderTranslations(
        cleanedBitmap: Bitmap,
        regions: List<TextRegion>,
        translations: List<String>,
    ): Bitmap {
        val resultBitmap = cleanedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val fontName = preferences.readerFont.get()
        val typeface = getTypeface(fontName)

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            isAntiAlias = true
            this.typeface = typeface
        }

        for (i in regions.indices) {
            if (i >= translations.size) break
            val text = translations[i]
            if (text.isBlank()) continue

            val rect = regions[i].rect
            drawScaledText(canvas, text, rect, textPaint)
        }

        return resultBitmap
    }

    private fun getTypeface(fontName: String): Typeface {
        return when (fontName) {
            TranslationPreferences.FONT_WILD_WORDS -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            TranslationPreferences.FONT_ANIME_ACE -> Typeface.create("casual", Typeface.BOLD)
            TranslationPreferences.FONT_MANGA_TEMPLE -> Typeface.create("serif", Typeface.BOLD)
            else -> Typeface.DEFAULT_BOLD
        }
    }

    private fun drawScaledText(canvas: Canvas, text: String, rect: Rect, paint: TextPaint) {
        val maxWidth = (rect.width() * 0.9f).coerceAtLeast(10f).toInt()
        val maxHeight = (rect.height() * 0.9f).coerceAtLeast(10f).toInt()

        var fontSize = 36f
        var layout: StaticLayout

        while (fontSize > 10f) {
            paint.textSize = fontSize
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

            if (layout.height <= maxHeight) {
                break
            }
            fontSize -= 2f
        }

        paint.textSize = fontSize
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        val translateX = rect.left + (rect.width() - layout.width) / 2f
        val translateY = rect.top + (rect.height() - layout.height) / 2f
        canvas.translate(translateX, translateY)
        layout.draw(canvas)
        canvas.restore()
    }
}
