package eu.kanade.tachiyomi.data.translation

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
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
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.File
import java.io.FileOutputStream

@Inject
@SingleIn(AppScope::class)
class TranslationModelManager(
    private val context: Context,
    private val networkClient: OkHttpClient,
    private val translationPreferences: TranslationPreferences,
) {
    enum class ModelType(
        val filename: String,
        val url: String,
        val title: String,
    ) {
        TEXT_DETECTOR(
            "detector-v4-s_int8.onnx",
            "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx",
            "Text & Bubble Detector (ONNX)",
        ),
        OCR_REC(
            "PP-OCRv6_small_rec.onnx",
            "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx",
            "PP-OCRv6 Recognition (ONNX)",
        ),
        INPAINTING(
            "aot.onnx",
            "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx",
            "AOT Inpainting Cleaner (ONNX)",
        ),
    }

    data class ModelDownloadState(
        val isDownloading: Boolean = false,
        val progress: Float = 0f,
        val error: String? = null,
    )

    private val _downloadState = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadState: StateFlow<Map<String, ModelDownloadState>> = _downloadState.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "translation_models").apply { if (!exists()) mkdirs() }

    fun getModelFile(type: ModelType): File {
        return File(modelsDir, type.filename)
    }

    fun isModelDownloaded(type: ModelType): Boolean {
        val file = getModelFile(type)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadModel(type: ModelType) = withContext(Dispatchers.IO) {
        updateState(type.name, ModelDownloadState(isDownloading = true, progress = 0f))
        try {
            val request = Request.Builder().url(type.url).build()
            val response = networkClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("HTTP error ${response.code}")
            }

            val body = response.body ?: error("Empty response body")
            val totalSize = body.contentLength()
            val destinationFile = getModelFile(type)
            val tempFile = File(modelsDir, "${type.filename}.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (totalSize > 0) totalRead.toFloat() / totalSize else 0f
                        updateState(type.name, ModelDownloadState(isDownloading = true, progress = progress))
                    }
                    output.flush()
                }
            }

            if (destinationFile.exists()) destinationFile.delete()
            tempFile.renameTo(destinationFile)

            updateState(type.name, ModelDownloadState(isDownloading = false, progress = 1f))
        } catch (e: Exception) {
            updateState(type.name, ModelDownloadState(isDownloading = false, error = e.message ?: "Download failed"))
        }
    }

    suspend fun deleteModel(type: ModelType) = withContext(Dispatchers.IO) {
        val file = getModelFile(type)
        if (file.exists()) {
            file.delete()
        }
        updateState(type.name, ModelDownloadState(isDownloading = false))
    }

    suspend fun isMlKitModelDownloaded(languageCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val remoteModelManager = RemoteModelManager.getInstance()
            val downloadedModels = remoteModelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            // Await task
            val models = com.google.android.gms.tasks.Tasks.await(downloadedModels)
            models.any { it.language == model.language }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadMlKitModel(languageCode: String) = withContext(Dispatchers.IO) {
        val key = "MLKIT_$languageCode"
        updateState(key, ModelDownloadState(isDownloading = true, progress = 0f))
        try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val conditions = DownloadConditions.Builder().build()
            val task = RemoteModelManager.getInstance().download(model, conditions)
            com.google.android.gms.tasks.Tasks.await(task)
            updateState(key, ModelDownloadState(isDownloading = false, progress = 1f))
        } catch (e: Exception) {
            updateState(key, ModelDownloadState(isDownloading = false, error = e.message ?: "Download failed"))
        }
    }

    private fun updateState(key: String, state: ModelDownloadState) {
        _downloadState.value = _downloadState.value.toMutableMap().apply { put(key, state) }
    }
}
