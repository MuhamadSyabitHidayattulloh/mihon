package eu.kanade.tachiyomi.data.translation.ai

import android.graphics.RectF

data class TextBoundingBox(
    val box: RectF,
    val originalText: String = "",
    val translatedText: String = "",
    val confidence: Float = 1.0f,
)
