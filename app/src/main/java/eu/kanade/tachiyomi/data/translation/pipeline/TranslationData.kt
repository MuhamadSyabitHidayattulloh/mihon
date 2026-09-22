package eu.kanade.tachiyomi.data.translation.pipeline

import android.graphics.RectF

data class DetectedBlock(
    val boundingBox: RectF,
    val confidence: Float = 1.0f,
)

data class TextBlock(
    val boundingBox: RectF,
    val originalText: String,
    var translatedText: String = "",
)
