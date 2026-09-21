package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class CleanerEngine {
    /**
     * Cleans text regions on a bitmap (inpainting speech bubbles with background color).
     */
    fun cleanTextRegions(originalBitmap: Bitmap, regions: List<TextRegion>): Bitmap {
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (region in regions) {
            val rect = region.boundingBox
            // Sample background color near top-left of region
            val sampleX = rect.left.coerceIn(0, mutableBitmap.width - 1)
            val sampleY = rect.top.coerceIn(0, mutableBitmap.height - 1)
            val bgColor = mutableBitmap.getPixel(sampleX, sampleY)

            // If background color is dark, default to white for speech bubble fill
            val r = Color.red(bgColor)
            val g = Color.green(bgColor)
            val b = Color.blue(bgColor)
            val brightness = (r * 299 + g * 587 + b * 114) / 1000

            paint.color = if (brightness > 150) bgColor else Color.WHITE
            canvas.drawRect(rect, paint)
        }

        return mutableBitmap
    }
}
