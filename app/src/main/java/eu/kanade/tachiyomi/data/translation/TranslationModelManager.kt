package eu.kanade.tachiyomi.data.translation

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.File

@Inject
@SingleIn(AppScope::class)
class TranslationModelManager(
    private val context: Context,
) {
    val modelsDir: File
        get() = File(context.filesDir, "translation_models").apply { if (!exists()) mkdirs() }

    val fontsDir: File
        get() = File(context.filesDir, "translation_fonts").apply { if (!exists()) mkdirs() }

    val detectorModelFile: File
        get() = File(modelsDir, "detector-v4-s_int8.onnx")

    val ocrModelFile: File
        get() = File(modelsDir, "PP-OCRv6_small_rec.onnx")

    val inpaintingModelFile: File
        get() = File(modelsDir, "aot.onnx")

    fun isDetectorDownloaded(): Boolean = detectorModelFile.exists() && detectorModelFile.length() > 0
    fun isOcrDownloaded(): Boolean = ocrModelFile.exists() && ocrModelFile.length() > 0
    fun isInpaintingDownloaded(): Boolean = inpaintingModelFile.exists() && inpaintingModelFile.length() > 0

    fun areAllModelsDownloaded(): Boolean = isDetectorDownloaded() && isOcrDownloaded() && isInpaintingDownloaded()

    fun getFontFile(fontName: String): File {
        val fileName = when (fontName) {
            TranslationPreferences.FONT_ANIME_ACE -> "AnimeAce.ttf"
            TranslationPreferences.FONT_CC_WILD_WORDS -> "CCWildWords.ttf"
            TranslationPreferences.FONT_KOMIKA_AXIS -> "KomikaAxis.ttf"
            TranslationPreferences.FONT_BANGERS -> "Bangers.ttf"
            TranslationPreferences.FONT_COMIC_NEUE -> "ComicNeue.ttf"
            else -> "AnimeAce.ttf"
        }
        return File(fontsDir, fileName)
    }

    fun isFontDownloaded(fontName: String): Boolean {
        val file = getFontFile(fontName)
        return file.exists() && file.length() > 0
    }

    fun downloadModel(url: String, fileName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Downloading Translation Model: $fileName")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        downloadManager.enqueue(request)
    }

    fun downloadFont(fontName: String) {
        val url = when (fontName) {
            TranslationPreferences.FONT_ANIME_ACE ->
                "https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/ComicNeue-Bold.ttf"
            TranslationPreferences.FONT_CC_WILD_WORDS ->
                "https://raw.githubusercontent.com/google/fonts/main/ofl/bangers/Bangers-Regular.ttf"
            TranslationPreferences.FONT_KOMIKA_AXIS ->
                "https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/ComicNeue-Regular.ttf"
            TranslationPreferences.FONT_BANGERS ->
                "https://raw.githubusercontent.com/google/fonts/main/ofl/bangers/Bangers-Regular.ttf"
            TranslationPreferences.FONT_COMIC_NEUE ->
                "https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/ComicNeue-Bold.ttf"
            else -> "https://raw.githubusercontent.com/google/fonts/main/ofl/comicneue/ComicNeue-Bold.ttf"
        }
        val fileName = getFontFile(fontName).name
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Downloading Font: $fontName")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        downloadManager.enqueue(request)
    }

    companion object {
        const val DETECTOR_URL =
            "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx"
        const val OCR_URL = "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx"
        const val INPAINTING_URL = "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx"
    }
}
