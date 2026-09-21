package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

interface InpaintingEngine {
    suspend fun inpaint(bitmap: Bitmap, regions: List<DetectedTextRegion>): Bitmap
}

class DefaultInpaintingEngine : InpaintingEngine {
    override suspend fun inpaint(bitmap: Bitmap, regions: List<DetectedTextRegion>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        for (region in regions) {
            val rect = region.boundingBox
            val paddedRect = Rect(
                (rect.left - 4).coerceAtLeast(0),
                (rect.top - 4).coerceAtLeast(0),
                (rect.right + 4).coerceAtMost(bitmap.width),
                (rect.bottom + 4).coerceAtMost(bitmap.height),
            )
            canvas.drawRect(paddedRect, paint)
        }
        return result
    }
}
