package eu.kanade.tachiyomi.data.translation.redraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.data.translation.onnx.TextDetector.BoundingBox

class CanvasRedrawer {

    data class RedrawItem(
        val box: BoundingBox,
        val originalText: String,
        val translatedText: String,
    )

    fun redraw(
        inpaintedBitmap: Bitmap,
        items: List<RedrawItem>,
    ): Bitmap {
        val result = inpaintedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        for (item in items) {
            val rect = item.box.rect
            val text = item.translatedText
            if (text.isBlank()) continue

            drawTextInRect(canvas, text, rect)
        }

        return result
    }

    private fun drawTextInRect(canvas: Canvas, text: String, rect: RectF) {
        val width = rect.width().toInt()
        val height = rect.height().toInt()
        if (width <= 0 || height <= 0) return

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = (height * 0.35f).coerceIn(12f, 40f)
        }

        var staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        // Adjust text size to fit box vertically
        while (staticLayout.height > height && textPaint.textSize > 8f) {
            textPaint.textSize -= 1f
            staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
        }

        canvas.save()
        val topOffset = rect.top + maxOf(0f, (height - staticLayout.height) / 2f)
        canvas.translate(rect.left, topOffset)
        staticLayout.draw(canvas)
        canvas.restore()
    }
}
