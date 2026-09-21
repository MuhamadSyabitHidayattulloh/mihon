package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

class TextDetector {
    /**
     * Detects probable text regions/speech bubbles on a manga page bitmap.
     */
    fun detectTextRegions(bitmap: Bitmap): List<TextRegion> {
        val width = bitmap.width
        val height = bitmap.height
        val regions = mutableListOf<TextRegion>()

        // Simple region detection: look for white/light speech bubbles or high contrast areas
        // Grid sampling to find speech bubbles or text blocks
        val stepX = (width / 20).coerceAtLeast(10)
        val stepY = (height / 30).coerceAtLeast(10)

        val visited = Array(width / stepX + 1) { BooleanArray(height / stepY + 1) }

        for (i in 0 until width / stepX) {
            for (j in 0 until height / stepY) {
                if (visited[i][j]) continue
                val px = (i * stepX + stepX / 2).coerceIn(0, width - 1)
                val py = (j * stepY + stepY / 2).coerceIn(0, height - 1)
                val pixel = bitmap.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // High brightness (bubble area) or text contrast
                val brightness = (r * 299 + g * 587 + b * 114) / 1000
                if (brightness > 220) {
                    val rect = expandBubbleRegion(bitmap, px, py)
                    if (rect.width() in 40..(width * 0.9).toInt() &&
                        rect.height() in 20..(height * 0.9).toInt()
                    ) {
                        // Check if not overlapping existing region heavily
                        val isOverlap = regions.any { Rect.intersects(it.boundingBox, rect) }
                        if (!isOverlap) {
                            regions.add(TextRegion(boundingBox = rect))
                        }
                    }
                }
            }
        }

        // If no speech bubble detected, fallback to central panel grid regions
        if (regions.isEmpty()) {
            val marginX = (width * 0.1).toInt()
            val marginY = (height * 0.1).toInt()
            val boxWidth = width - marginX * 2
            val boxHeight = (height * 0.15).toInt()
            regions.add(TextRegion(boundingBox = Rect(marginX, marginY, marginX + boxWidth, marginY + boxHeight)))
        }

        return regions
    }

    private fun expandBubbleRegion(bitmap: Bitmap, startX: Int, startY: Int): Rect {
        val width = bitmap.width
        val height = bitmap.height
        var left = startX
        var right = startX
        var top = startY
        var bottom = startY

        // Expand left
        while (left > 0) {
            val p = bitmap.getPixel(left, startY)
            if (isWhiteOrLight(p)) left -= 5 else break
        }
        // Expand right
        while (right < width - 1) {
            val p = bitmap.getPixel(right, startY)
            if (isWhiteOrLight(p)) right += 5 else break
        }
        // Expand top
        while (top > 0) {
            val p = bitmap.getPixel(startX, top)
            if (isWhiteOrLight(p)) top -= 5 else break
        }
        // Expand bottom
        while (bottom < height - 1) {
            val p = bitmap.getPixel(startX, bottom)
            if (isWhiteOrLight(p)) bottom += 5 else break
        }

        return Rect(
            left.coerceIn(0, width),
            top.coerceIn(0, height),
            right.coerceIn(0, width),
            bottom.coerceIn(0, height),
        )
    }

    private fun isWhiteOrLight(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (r * 299 + g * 587 + b * 114) / 1000 > 200
    }
}
