package eu.kanade.domain.translation.onnx

import android.content.Context
import androidx.core.app.NotificationCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

data class TranslationModelsStatus(
    val detectorStatus: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val ocrStatus: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val inpaintingStatus: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
)

@Inject
@SingleIn(AppScope::class)
class TranslationModelDownloader(
    private val context: Context,
    private val networkHelper: NetworkHelper,
) {
    val modelsDir = File(context.filesDir, "translation_models").apply { mkdirs() }

    val detectorFile = File(modelsDir, DETECTOR_FILENAME)
    val ocrFile = File(modelsDir, OCR_FILENAME)
    val inpaintingFile = File(modelsDir, INPAINTING_FILENAME)

    private val _status = MutableStateFlow(getInitialStatus())
    val status: StateFlow<TranslationModelsStatus> = _status.asStateFlow()

    fun getInitialStatus(): TranslationModelsStatus {
        val detDownloaded = detectorFile.exists() && detectorFile.length() > 0L
        val ocrDownloaded = ocrFile.exists() && ocrFile.length() > 0L
        val inpDownloaded = inpaintingFile.exists() && inpaintingFile.length() > 0L
        val detStatus = if (detDownloaded) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.NOT_DOWNLOADED
        val ocrStatus = if (ocrDownloaded) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.NOT_DOWNLOADED
        val inpStatus = if (inpDownloaded) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.NOT_DOWNLOADED
        return TranslationModelsStatus(
            detectorStatus = detStatus,
            ocrStatus = ocrStatus,
            inpaintingStatus = inpStatus,
        )
    }

    fun isAllModelsDownloaded(): Boolean {
        return detectorFile.exists() && detectorFile.length() > 0L &&
            ocrFile.exists() && ocrFile.length() > 0L &&
            inpaintingFile.exists() && inpaintingFile.length() > 0L
    }

    suspend fun downloadAllModels() = withContext(Dispatchers.IO) {
        val notificationId = Notifications.ID_TRANSLATION_MODEL_DOWNLOAD
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
            .setContentTitle("Downloading Translation Models")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        try {
            context.notificationManager.notify(notificationId, builder.build())

            if (!detectorFile.exists() || detectorFile.length() == 0L) {
                _status.value = _status.value.copy(detectorStatus = ModelDownloadStatus.DOWNLOADING)
                downloadFile(DETECTOR_URL, detectorFile) { progress ->
                    builder.setContentText("Downloading Text Detector ($progress%)")
                    builder.setProgress(100, progress, false)
                    context.notificationManager.notify(notificationId, builder.build())
                }
                _status.value = _status.value.copy(detectorStatus = ModelDownloadStatus.DOWNLOADED)
            }

            if (!ocrFile.exists() || ocrFile.length() == 0L) {
                _status.value = _status.value.copy(ocrStatus = ModelDownloadStatus.DOWNLOADING)
                downloadFile(OCR_URL, ocrFile) { progress ->
                    builder.setContentText("Downloading OCR Model ($progress%)")
                    builder.setProgress(100, progress, false)
                    context.notificationManager.notify(notificationId, builder.build())
                }
                _status.value = _status.value.copy(ocrStatus = ModelDownloadStatus.DOWNLOADED)
            }

            if (!inpaintingFile.exists() || inpaintingFile.length() == 0L) {
                _status.value = _status.value.copy(inpaintingStatus = ModelDownloadStatus.DOWNLOADING)
                downloadFile(INPAINTING_URL, inpaintingFile) { progress ->
                    builder.setContentText("Downloading Inpainting Model ($progress%)")
                    builder.setProgress(100, progress, false)
                    context.notificationManager.notify(notificationId, builder.build())
                }
                _status.value = _status.value.copy(inpaintingStatus = ModelDownloadStatus.DOWNLOADED)
            }

            builder.setContentTitle("Translation Models Downloaded")
                .setContentText("All models are ready")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setProgress(0, 0, false)
            context.notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            val detS = if (detectorFile.exists()) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.ERROR
            val ocrS = if (ocrFile.exists()) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.ERROR
            val inpS = if (inpaintingFile.exists()) ModelDownloadStatus.DOWNLOADED else ModelDownloadStatus.ERROR
            _status.value = TranslationModelsStatus(
                detectorStatus = detS,
                ocrStatus = ocrS,
                inpaintingStatus = inpS,
            )

            builder.setContentTitle("Model Download Failed")
                .setContentText(e.localizedMessage ?: "Download error")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false)
                .setProgress(0, 0, false)
            context.notificationManager.notify(notificationId, builder.build())
            throw e
        }
    }

    private fun downloadFile(url: String, targetFile: File, onProgress: (Int) -> Unit) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        val request = Request.Builder().url(url).build()

        networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed HTTP ${response.code} for $url")
            val body = response.body
            val contentLength = body.contentLength()
            var totalRead = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastProgress = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }
        if (targetFile.exists()) targetFile.delete()
        tempFile.renameTo(targetFile)
    }

    companion object {
        const val DETECTOR_FILENAME = "detector-v4-s_int8.onnx"
        const val OCR_FILENAME = "PP-OCRv6_small_rec.onnx"
        const val INPAINTING_FILENAME = "aot.onnx"

        const val DETECTOR_URL =
            "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx"
        const val OCR_URL =
            "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx"
        const val INPAINTING_URL =
            "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx"
    }
}
