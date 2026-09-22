package eu.kanade.tachiyomi.data.translation.ai

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.File
import java.io.FileOutputStream

@Inject
@SingleIn(AppScope::class)
class ModelManager(
    private val context: Context,
    private val networkHelper: NetworkHelper,
    private val translationPreferences: TranslationPreferences,
) {

    data class DownloadProgress(
        val modelName: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isCompleted: Boolean,
        val error: String? = null,
    ) {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    }

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadProgress>> = _downloadStatus.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "translation_models").also { if (!it.exists()) it.mkdirs() }

    fun getModelFile(fileName: String): File {
        return File(modelsDir, fileName)
    }

    fun isModelAvailable(fileName: String): Boolean {
        val file = getModelFile(fileName)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadModel(modelName: String, url: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val targetFile = getModelFile(fileName)
            try {
                _downloadStatus.value = _downloadStatus.value + (modelName to DownloadProgress(modelName, 0, -1, false))
                val request = Request.Builder().url(url).build()
                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _downloadStatus.value =
                            _downloadStatus.value +
                            (modelName to DownloadProgress(modelName, 0, -1, false, "HTTP ${response.code}"))
                        return@withContext false
                    }
                    val body = response.body
                    val totalBytes = body.contentLength()
                    var bytesDownloaded = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesDownloaded += read
                                _downloadStatus.value = _downloadStatus.value + (
                                    modelName to DownloadProgress(modelName, bytesDownloaded, totalBytes, false)
                                    )
                            }
                            output.flush()
                        }
                    }

                    _downloadStatus.value = _downloadStatus.value + (
                        modelName to DownloadProgress(modelName, bytesDownloaded, totalBytes, true)
                        )

                    updatePreferenceForModel(modelName, true)
                    true
                }
            } catch (e: Exception) {
                _downloadStatus.value = _downloadStatus.value + (
                    modelName to DownloadProgress(modelName, 0, -1, false, e.message ?: "Download failed")
                    )
                false
            }
        }
    }

    private fun updatePreferenceForModel(modelName: String, downloaded: Boolean) {
        when {
            modelName.contains("detection", ignoreCase = true) ->
                translationPreferences.detectionModelDownloaded.set(downloaded)
            modelName.contains("ocr", ignoreCase = true) ->
                translationPreferences.ocrModelDownloaded.set(downloaded)
            modelName.contains("inpainting", ignoreCase = true) ->
                translationPreferences.inpaintingModelDownloaded.set(downloaded)
        }
    }

    companion object {
        const val MODEL_DETECTION = "Comic Text Detection"
        const val URL_DETECTION =
            "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx"
        const val FILE_DETECTION = "detector-v4-s_int8.onnx"

        const val MODEL_OCR = "PaddleOCR v6"
        const val URL_OCR =
            "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx"
        const val FILE_OCR = "PP-OCRv6_small_rec.onnx"

        const val MODEL_INPAINTING = "AOT Inpainting"
        const val URL_INPAINTING =
            "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx"
        const val FILE_INPAINTING = "aot.onnx"
    }
}
