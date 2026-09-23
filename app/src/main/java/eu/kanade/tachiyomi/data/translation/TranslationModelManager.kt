package eu.kanade.tachiyomi.data.translation

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed class ModelDownloadState {
    data object NotDownloaded : ModelDownloadState()
    data class Downloading(val progress: Int) : ModelDownloadState()
    data object Downloaded : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

data class ModelInfo(
    val key: String,
    val name: String,
    val url: String,
    val fileName: String,
)

@Inject
@SingleIn(AppScope::class)
class TranslationModelManager(
    private val context: Context,
    private val networkHelper: NetworkHelper,
) {
    private val okHttpClient get() = networkHelper.client

    private val scope = CoroutineScope(Dispatchers.IO)
    private val modelsDir = File(context.filesDir, "translation_models").apply { mkdirs() }

    val detectorModel = ModelInfo(
        key = KEY_DETECTOR,
        name = "Comic Text & Bubble Detector",
        url = "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx",
        fileName = "detector-v4-s_int8.onnx",
    )

    val ocrModel = ModelInfo(
        key = KEY_OCR,
        name = "PP-OCRv6 Small Rec",
        url = "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx",
        fileName = "PP-OCRv6_small_rec.onnx",
    )

    val inpaintingModel = ModelInfo(
        key = KEY_INPAINTING,
        name = "AOT Inpainting Cleaner",
        url = "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx",
        fileName = "aot.onnx",
    )

    private val _modelStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val modelStates: StateFlow<Map<String, ModelDownloadState>> = _modelStates.asStateFlow()

    init {
        checkAllModels()
    }

    fun checkAllModels() {
        listOf(detectorModel, ocrModel, inpaintingModel).forEach { info ->
            val file = File(modelsDir, info.fileName)
            if (file.exists() && file.length() > 0) {
                _modelStates.update { it + (info.key to ModelDownloadState.Downloaded) }
            } else {
                _modelStates.update { it + (info.key to ModelDownloadState.NotDownloaded) }
            }
        }
    }

    fun getModelFile(key: String): File? {
        val fileName = when (key) {
            KEY_DETECTOR -> detectorModel.fileName
            KEY_OCR -> ocrModel.fileName
            KEY_INPAINTING -> inpaintingModel.fileName
            else -> return null
        }
        val file = File(modelsDir, fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun downloadModel(key: String) {
        val info = when (key) {
            KEY_DETECTOR -> detectorModel
            KEY_OCR -> ocrModel
            KEY_INPAINTING -> inpaintingModel
            else -> {
                downloadMlKitModel(key)
                return
            }
        }

        scope.launch {
            _modelStates.update { it + (key to ModelDownloadState.Downloading(0)) }
            val destFile = File(modelsDir, info.fileName)
            try {
                val request = Request.Builder().url(info.url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _modelStates.update { it + (key to ModelDownloadState.Error("HTTP ${response.code}")) }
                        return@launch
                    }
                    val body = response.body
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    val tempFile = File(modelsDir, "${info.fileName}.tmp")
                    val outputStream = FileOutputStream(tempFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            _modelStates.update { it + (key to ModelDownloadState.Downloading(progress)) }
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)

                    _modelStates.update { it + (key to ModelDownloadState.Downloaded) }
                }
            } catch (e: Exception) {
                _modelStates.update { it + (key to ModelDownloadState.Error(e.localizedMessage ?: "Download failed")) }
            }
        }
    }

    private fun downloadMlKitModel(langCode: String) {
        scope.launch {
            val translateLang = TranslateLanguage.fromLanguageTag(langCode) ?: langCode
            val model = TranslateRemoteModel.Builder(translateLang).build()
            val manager = RemoteModelManager.getInstance()

            _modelStates.update { it + (langCode to ModelDownloadState.Downloading(0)) }
            val conditions = DownloadConditions.Builder().build()
            manager.download(model, conditions)
                .addOnSuccessListener {
                    _modelStates.update { it + (langCode to ModelDownloadState.Downloaded) }
                }
                .addOnFailureListener { e ->
                    _modelStates.update { it + (langCode to ModelDownloadState.Error(e.localizedMessage ?: "Failed")) }
                }
        }
    }

    fun deleteModel(key: String) {
        val info = when (key) {
            KEY_DETECTOR -> detectorModel
            KEY_OCR -> ocrModel
            KEY_INPAINTING -> inpaintingModel
            else -> null
        }
        if (info != null) {
            val file = File(modelsDir, info.fileName)
            if (file.exists()) file.delete()
            _modelStates.update { it + (key to ModelDownloadState.NotDownloaded) }
        } else {
            val translateLang = TranslateLanguage.fromLanguageTag(key) ?: key
            val model = TranslateRemoteModel.Builder(translateLang).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model)
                .addOnCompleteListener {
                    _modelStates.update { it + (key to ModelDownloadState.NotDownloaded) }
                }
        }
    }

    companion object {
        const val KEY_DETECTOR = "detector"
        const val KEY_OCR = "ocr"
        const val KEY_INPAINTING = "inpainting"
    }
}
