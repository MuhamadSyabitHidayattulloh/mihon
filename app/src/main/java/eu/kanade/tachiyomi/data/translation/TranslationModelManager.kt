package eu.kanade.tachiyomi.data.translation

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.io.FileOutputStream

@Inject
@SingleIn(AppScope::class)
class TranslationModelManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    val modelsDir: File
        get() = File(context.filesDir, "translation_models").apply { if (!exists()) mkdirs() }

    enum class ModelType(val fileName: String, val url: String, val displayName: String) {
        DETECTOR(
            "detector-v4-s_int8.onnx",
            "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx",
            "Text & Bubble Detector (ONNX)",
        ),
        OCR(
            "PP-OCRv6_small_rec.onnx",
            "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx",
            "PP-OCR v6 (ONNX)",
        ),
        INPAINTING(
            "aot.onnx",
            "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx",
            "AOT Inpainting (ONNX)",
        ),
    }

    sealed class DownloadState {
        object NotDownloaded : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Downloaded : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _downloadStates = MutableStateFlow<Map<ModelType, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<ModelType, DownloadState>> = _downloadStates.asStateFlow()

    init {
        updateStates()
    }

    fun updateStates() {
        val currentMap = mutableMapOf<ModelType, DownloadState>()
        ModelType.entries.forEach { type ->
            val file = getModelFile(type)
            currentMap[type] = if (file.exists() && file.length() > 0) {
                DownloadState.Downloaded
            } else {
                DownloadState.NotDownloaded
            }
        }
        _downloadStates.value = currentMap
    }

    fun getModelFile(type: ModelType): File {
        return File(modelsDir, type.fileName)
    }

    fun isModelDownloaded(type: ModelType): Boolean {
        val file = getModelFile(type)
        return file.exists() && file.length() > 0
    }

    fun areAllModelsDownloaded(): Boolean {
        return ModelType.entries.all { isModelDownloaded(it) }
    }

    suspend fun downloadModel(type: ModelType) = withContext(Dispatchers.IO) {
        val destination = getModelFile(type)
        val tempFile = File(modelsDir, "${type.fileName}.tmp")

        _downloadStates.value = _downloadStates.value + (type to DownloadState.Downloading(0f))

        try {
            val request = Request.Builder().url(type.url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                _downloadStates.value = _downloadStates.value + (type to DownloadState.Error("HTTP ${response.code}"))
                return@withContext
            }

            val body = response.body
            val contentLength = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = totalRead.toFloat() / contentLength.toFloat()
                            _downloadStates.value = _downloadStates.value + (type to DownloadState.Downloading(progress))
                        }
                    }
                }
            }

            if (tempFile.exists()) {
                if (destination.exists()) destination.delete()
                tempFile.renameTo(destination)
            }

            _downloadStates.value = _downloadStates.value + (type to DownloadState.Downloaded)
        } catch (e: Exception) {
            tempFile.delete()
            _downloadStates.value = _downloadStates.value + (type to DownloadState.Error(e.localizedMessage ?: "Download failed"))
        }
    }

    fun deleteModel(type: ModelType) {
        val file = getModelFile(type)
        if (file.exists()) {
            file.delete()
        }
        updateStates()
    }
}
