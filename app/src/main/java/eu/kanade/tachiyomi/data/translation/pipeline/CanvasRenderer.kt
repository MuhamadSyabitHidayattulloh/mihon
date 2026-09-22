package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

class CanvasRenderer {

    fun render(
        bitmap: Bitmap,
        textBlocks: List<TextBlock>,
    ): Bitmap {
        if (textBlocks.isEmpty()) return bitmap

        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        for (block in textBlocks) {
            val text = block.translatedText.ifBlank { block.originalText }
            if (text.isBlank()) continue

            val box = block.boundingBox
            val boxWidth = max(10, box.width().toInt())
            val boxHeight = max(10, box.height().toInt())

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }

            var fontSize = 28f
            var layout: StaticLayout

            while (fontSize > 10f) {
                textPaint.textSize = fontSize
                layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, boxWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .build()

                if (layout.height <= boxHeight) {
                    break
                }
                fontSize -= 2f
            }

            textPaint.textSize = fontSize
            layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, boxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()

            val textHeight = layout.height
            val x = box.left
            val y = box.top + (boxHeight - textHeight) / 2f

            canvas.save()
            canvas.translate(x, max(box.top, y))

            // White background padding behind text if needed for readability
            val strokePaint = TextPaint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = fontSize * 0.15f
                color = Color.WHITE
            }
            val strokeLayout = StaticLayout.Builder.obtain(text, 0, text.length, strokePaint, boxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()
            strokeLayout.draw(canvas)

            layout.draw(canvas)
            canvas.restore()
        }

        return outputBitmap
    }
}
