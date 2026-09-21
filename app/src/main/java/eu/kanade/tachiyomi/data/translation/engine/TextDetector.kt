package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

data class TextRegion(
    val rect: Rect,
    val sampleText: String = "",
)

class TextDetector {

    /**
     * Detects text / speech bubble regions in a comic image bitmap.
     * Uses brightness and edge variance detection to identify text/bubble bounding boxes.
     */
    fun detectTextRegions(bitmap: Bitmap): List<TextRegion> {
        val width = bitmap.width
        val height = bitmap.height
        val regions = mutableListOf<TextRegion>()

        val gridCols = 10
        val gridRows = 15
        val cellW = width / gridCols
        val cellH = height / gridRows

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                val left = c * cellW
                val top = r * cellH
                val right = (left + cellW).coerceAtMost(width)
                val bottom = (top + cellH).coerceAtMost(height)

                if (isHighContrastRegion(pixels, width, left, top, right, bottom)) {
                    regions.add(TextRegion(Rect(left, top, right, bottom)))
                }
            }
        }

        return mergeAdjacentRegions(regions, width, height)
    }

    private fun isHighContrastRegion(
        pixels: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        var minLuminance = 255
        var maxLuminance = 0
        var sampleCount = 0

        val stepX = ((right - left) / 8).coerceAtLeast(1)
        val stepY = ((bottom - top) / 8).coerceAtLeast(1)

        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val pixel = pixels[y * stride + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                if (lum < minLuminance) minLuminance = lum
                if (lum > maxLuminance) maxLuminance = lum
                sampleCount++
            }
        }

        return (maxLuminance - minLuminance) > 160
    }

    private fun mergeAdjacentRegions(regions: List<TextRegion>, width: Int, height: Int): List<TextRegion> {
        if (regions.isEmpty()) return emptyList()

        val merged = mutableListOf<Rect>()
        for (region in regions) {
            val rect = region.rect
            var added = false
            for (i in merged.indices) {
                val m = merged[i]
                if (Rect.intersects(m, rect) || isClose(m, rect, width / 20)) {
                    m.union(rect)
                    added = true
                    break
                }
            }
            if (!added) {
                merged.add(Rect(rect))
            }
        }

        return merged.map { TextRegion(it) }
    }

    private fun isClose(r1: Rect, r2: Rect, threshold: Int): Boolean {
        val dx = (r1.centerX() - r2.centerX()).let { Math.abs(it) } - (r1.width() + r2.width()) / 2
        val dy = (r1.centerY() - r2.centerY()).let { Math.abs(it) } - (r1.height() + r2.height()) / 2
        return dx <= threshold && dy <= threshold
    }
}
