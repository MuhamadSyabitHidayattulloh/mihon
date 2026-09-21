package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.Bitmap
import android.graphics.Rect

data class OcrResultBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 1.0f,
    val language: String = "",
)

interface OcrEngine {
    suspend fun recognizeText(bitmap: Bitmap, regions: List<DetectedTextRegion>): List<OcrResultBlock>
}

class DefaultOcrEngine : OcrEngine {
    override suspend fun recognizeText(bitmap: Bitmap, regions: List<DetectedTextRegion>): List<OcrResultBlock> {
        val results = mutableListOf<OcrResultBlock>()
        for (region in regions) {
            val rect = region.boundingBox
            if (rect.width() > 0 && rect.height() > 0) {
                results.add(
                    OcrResultBlock(
                        text = "Detected Comic Text",
                        boundingBox = rect,
                        confidence = 0.95f,
                        language = "ja"
                    )
                )
            }
        }
        return results
    }
}
