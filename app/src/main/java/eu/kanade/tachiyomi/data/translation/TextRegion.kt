package eu.kanade.tachiyomi.data.translation

import android.graphics.Rect

data class TextRegion(
    val boundingBox: Rect,
    var originalText: String = "",
    var translatedText: String = "",
)
