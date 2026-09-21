package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class TextCleaner {

    /**
     * Cleans / inpaints text regions in a bitmap by filling with background color or surrounding average color.
     */
    fun cleanTextRegions(original: Bitmap, regions: List<TextRegion>): Bitmap {
        val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val pixels = IntArray(original.width * original.height)
        original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)

        for (region in regions) {
            val rect = region.rect
            val fillColor = getBorderAverageColor(pixels, original.width, rect.left, rect.top, rect.right, rect.bottom)
            paint.color = fillColor
            canvas.drawRect(rect, paint)
        }

        return mutableBitmap
    }

    private fun getBorderAverageColor(
        pixels: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Int {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val topY = top.coerceIn(0, (pixels.size / stride) - 1)
        val bottomY = (bottom - 1).coerceIn(0, (pixels.size / stride) - 1)

        for (x in left until right step 2) {
            val clampedX = x.coerceIn(0, stride - 1)

            val pTop = pixels[topY * stride + clampedX]
            rSum += Color.red(pTop)
            gSum += Color.green(pTop)
            bSum += Color.blue(pTop)

            val pBottom = pixels[bottomY * stride + clampedX]
            rSum += Color.red(pBottom)
            gSum += Color.green(pBottom)
            bSum += Color.blue(pBottom)

            count += 2
        }

        if (count == 0) return Color.WHITE
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }
}
