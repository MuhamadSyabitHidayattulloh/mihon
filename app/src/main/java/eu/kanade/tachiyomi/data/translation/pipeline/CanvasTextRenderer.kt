package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

class CanvasTextRenderer {

    fun renderTranslatedText(
        inpaintedBitmap: Bitmap,
        blocks: List<Pair<OcrResultBlock, String>>,
        typeface: Typeface = Typeface.DEFAULT,
    ): Bitmap {
        val output = inpaintedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        for ((block, translatedText) in blocks) {
            if (translatedText.isBlank()) continue
            val rect = block.boundingBox
            val width = rect.width().coerceAtLeast(20)
            val height = rect.height().coerceAtLeast(20)

            val textPaint = TextPaint().apply {
                color = Color.BLACK
                isAntiAlias = true
                this.typeface = typeface
                textSize = (height * 0.2f).coerceIn(12f, 48f)
            }

            var staticLayout = StaticLayout.Builder
                .obtain(translatedText, 0, translatedText.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            while (staticLayout.height > height && textPaint.textSize > 10f) {
                textPaint.textSize -= 1f
                staticLayout = StaticLayout.Builder
                    .obtain(translatedText, 0, translatedText.length, textPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()
            }

            canvas.save()
            val x = rect.left.toFloat()
            val y = rect.top + (height - staticLayout.height) / 2f
            canvas.translate(x, y.coerceAtLeast(rect.top.toFloat()))
            staticLayout.draw(canvas)
            canvas.restore()
        }

        return output
    }
}
