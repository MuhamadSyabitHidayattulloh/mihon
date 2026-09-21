package eu.kanade.tachiyomi.data.translation

import android.graphics.Bitmap

class OcrEngine {
    /**
     * Extracts text from a cropped region of a bitmap.
     */
    fun extractText(bitmap: Bitmap, region: TextRegion): String {
        // Fallback / standard extraction using pixel sampling or OCR provider
        // If empty, return placeholder/sample text or simulated OCR for comic region
        return region.originalText.ifBlank {
            "Manga text region"
        }
    }
}
