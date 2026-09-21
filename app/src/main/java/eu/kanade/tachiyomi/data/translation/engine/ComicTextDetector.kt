package eu.kanade.tachiyomi.data.translation.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class ComicTextDetector(private val context: Context) {

    private val paddleEngine = PaddleOcrEngine(context)

    suspend fun detectComicText(bitmap: Bitmap): List<OcrResultBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<OcrResultBlock>()
        try {
            blocks.addAll(paddleEngine.processImage(bitmap))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Comic text detection error" }
        }
        blocks
    }
}
