package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.Bitmap
import android.graphics.Rect

data class DetectedTextRegion(
    val boundingBox: Rect,
    val confidence: Float = 1.0f,
    val polygon: List<Pair<Float, Float>>? = null,
)

interface TextDetector {
    suspend fun detectTextRegions(bitmap: Bitmap): List<DetectedTextRegion>
}

class DefaultTextDetector : TextDetector {
    override suspend fun detectTextRegions(bitmap: Bitmap): List<DetectedTextRegion> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()

        val regions = mutableListOf<DetectedTextRegion>()
        regions.add(
            DetectedTextRegion(
                boundingBox = Rect(
                    (width * 0.1).toInt(),
                    (height * 0.05).toInt(),
                    (width * 0.9).toInt(),
                    (height * 0.25).toInt(),
                ),
            ),
        )
        regions.add(
            DetectedTextRegion(
                boundingBox = Rect(
                    (width * 0.1).toInt(),
                    (height * 0.70).toInt(),
                    (width * 0.9).toInt(),
                    (height * 0.90).toInt(),
                ),
            ),
        )
        return regions
    }
}
