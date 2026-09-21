package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.File
import java.io.FileOutputStream

enum class TranslationStatus {
    NOT_TRANSLATED,
    QUEUED,
    DETECTING,
    OCR,
    CLEANING,
    TRANSLATING,
    CANVAS_RENDERING,
    TRANSLATED,
    ERROR,
}

data class TranslationProgress(
    val status: TranslationStatus = TranslationStatus.NOT_TRANSLATED,
    val progress: Int = 0,
    val stageName: String = "",
    val logs: List<String> = emptyList(),
    val doneQueue: Int = 0,
    val failedQueue: Int = 0,
)

@Inject
@SingleIn(AppScope::class)
class TranslationManager(
    private val context: Context,
    private val preferences: TranslationPreferences,
    private val client: OkHttpClient,
) {
    private val states = MutableStateFlow<Map<Long, TranslationProgress>>(emptyMap())

    val textDetector = TextDetector()
    val ocrEngine = OcrEngine()
    val cleanerEngine = CleanerEngine()
    val canvasRenderer = CanvasRenderer()

    fun getProgressFlow(chapterId: Long): StateFlow<TranslationProgress> {
        val currentMap = states.value
        if (!currentMap.containsKey(chapterId)) {
            val initial = TranslationProgress()
            states.value = currentMap + (chapterId to initial)
        }
        return MutableStateFlow(states.value[chapterId] ?: TranslationProgress()).asStateFlow()
    }

    fun getStatus(chapterId: Long): TranslationStatus {
        return states.value[chapterId]?.status ?: TranslationStatus.NOT_TRANSLATED
    }

    fun updateProgress(
        chapterId: Long,
        status: TranslationStatus,
        progress: Int,
        stageName: String,
        logMessage: String? = null,
        doneInc: Int = 0,
        failedInc: Int = 0,
    ) {
        val currentMap = states.value
        val existing = currentMap[chapterId] ?: TranslationProgress()
        val newLogs = if (logMessage != null) existing.logs + logMessage else existing.logs
        val updated = existing.copy(
            status = status,
            progress = progress,
            stageName = stageName,
            logs = newLogs,
            doneQueue = existing.doneQueue + doneInc,
            failedQueue = existing.failedQueue + failedInc,
        )
        states.value = currentMap + (chapterId to updated)
    }

    suspend fun translateChapterDirectory(
        chapterId: Long,
        chapterDir: File,
    ) {
        updateProgress(
            chapterId = chapterId,
            status = TranslationStatus.QUEUED,
            progress = 0,
            stageName = "Queued",
            logMessage = "Memulai antrean terjemahan...",
        )

        val translationDir = File(chapterDir, "translations")
        if (!translationDir.exists()) {
            translationDir.mkdirs()
        }

        val imageFiles = chapterDir.listFiles { file ->
            file.isFile &&
                (
                    file.extension.equals("jpg", true) || file.extension.equals("png", true) ||
                        file.extension.equals("webp", true)
                    )
        }?.sortedBy { it.name } ?: emptyList()

        if (imageFiles.isEmpty()) {
            updateProgress(
                chapterId = chapterId,
                status = TranslationStatus.ERROR,
                progress = 0,
                stageName = "Gagal",
                logMessage = "Tidak ada gambar ditemukan di direktori chapter.",
                failedInc = 1,
            )
            return
        }

        val engine = createEngine()
        val fromLang = preferences.translateFrom.get()
        val toLang = preferences.translateTo.get()
        val font = preferences.readerFont.get()

        val total = imageFiles.size
        for ((index, imageFile) in imageFiles.withIndex()) {
            val stepPercent = ((index + 1) * 100) / total
            try {
                // Step 1: Text Detection
                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.DETECTING,
                    progress = stepPercent,
                    stageName = "Ocr/Deteksi (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Mendeteksi panel teks...",
                )
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: continue
                val regions = textDetector.detectTextRegions(bitmap)

                // Step 2: OCR
                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.OCR,
                    progress = stepPercent,
                    stageName = "Ocr/Deteksi (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Menjalankan OCR...",
                )
                for (region in regions) {
                    region.originalText = ocrEngine.extractText(bitmap, region)
                }

                // Step 3: Cleaning / Inpainting
                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.CLEANING,
                    progress = stepPercent,
                    stageName = "Cleaning (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Membersihkan area teks...",
                )
                val cleanedBitmap = cleanerEngine.cleanTextRegions(bitmap, regions)

                // Step 4: Translation
                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.TRANSLATING,
                    progress = stepPercent,
                    stageName = "Translation (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Menerjemahkan teks...",
                )
                for (region in regions) {
                    region.translatedText = engine.translate(region.originalText, fromLang, toLang)
                }

                // Step 5: Canvas Render
                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.CANVAS_RENDERING,
                    progress = stepPercent,
                    stageName = "Canvas Render (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Menggambar ulang teks terjemahan...",
                )
                val finalBitmap = canvasRenderer.renderTranslatedText(cleanedBitmap, regions, font)

                // Save translated file
                val outputFile = File(translationDir, imageFile.name)
                FileOutputStream(outputFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.CANVAS_RENDERING,
                    progress = stepPercent,
                    stageName = "Proses (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Berhasil diterjemahkan.",
                    doneInc = 1,
                )
            } catch (e: Exception) {
                updateProgress(
                    chapterId = chapterId,
                    status = TranslationStatus.ERROR,
                    progress = stepPercent,
                    stageName = "Gagal (${index + 1}/$total)",
                    logMessage = "[${imageFile.name}] Kesalahan: ${e.message}",
                    failedInc = 1,
                )
            }
        }

        val hasError = (states.value[chapterId]?.failedQueue ?: 0) > 0 && (states.value[chapterId]?.doneQueue ?: 0) == 0
        if (hasError) {
            updateProgress(
                chapterId = chapterId,
                status = TranslationStatus.ERROR,
                progress = 100,
                stageName = "Selesai dengan Kesalahan",
                logMessage = "Proses terjemahan selesai dengan kesalahan.",
            )
        } else {
            updateProgress(
                chapterId = chapterId,
                status = TranslationStatus.TRANSLATED,
                progress = 100,
                stageName = "Selesai",
                logMessage = "Seluruh halaman chapter berhasil diterjemahkan.",
            )
        }
    }

    private fun createEngine(): TranslationEngine {
        return when (preferences.translatorEngine.get()) {
            TranslationPreferences.ENGINE_GEMINI -> GeminiTranslateEngine(
                client = client,
                apiKey = preferences.geminiApiKey.get(),
            )
            TranslationPreferences.ENGINE_OPENROUTER -> OpenRouterTranslateEngine(
                client = client,
                apiKey = preferences.openRouterApiKey.get(),
                model = preferences.openRouterModel.get(),
            )
            TranslationPreferences.ENGINE_MLKIT -> MlKitOfflineTranslateEngine()
            else -> GoogleTranslateEngine(client = client)
        }
    }

    fun deleteTranslation(chapterDir: File) {
        val translationDir = File(chapterDir, "translations")
        if (translationDir.exists()) {
            translationDir.deleteRecursively()
        }
    }
}
