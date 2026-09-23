package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.translation.engine.GeminiTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.MlKitTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslatorEngine
import eu.kanade.tachiyomi.data.translation.engine.TranslationEngineProvider
import eu.kanade.tachiyomi.data.translation.onnx.AotInpainter
import eu.kanade.tachiyomi.data.translation.onnx.TextDetector
import eu.kanade.tachiyomi.data.translation.onnx.TextOcr
import eu.kanade.tachiyomi.data.translation.redraw.CanvasRedrawer
import okhttp3.OkHttpClient
import tachiyomi.domain.translation.model.OnnxModelType
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.service.TranslationPreferences
import java.io.File
import java.io.FileOutputStream

@Inject
@SingleIn(AppScope::class)
class TranslationPipelineManager(
    private val context: Context,
    private val preferences: TranslationPreferences,
    private val networkHelper: eu.kanade.tachiyomi.network.NetworkHelper,
) {
    private val client: OkHttpClient get() = networkHelper.client
    private val redrawer = CanvasRedrawer()

    private fun getModelFile(type: OnnxModelType): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, type.filename)
    }

    suspend fun processImage(imageBytes: ByteArray): ByteArray {
        val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return imageBytes

        val detectorFile = getModelFile(OnnxModelType.DETECTOR)
        val ocrRecFile = getModelFile(OnnxModelType.OCR_REC)
        val ocrKeysFile = getModelFile(OnnxModelType.OCR_KEYS)
        val inpaintFile = getModelFile(OnnxModelType.INPAINTING)

        val detector = TextDetector(detectorFile)
        val boxes = detector.detect(originalBitmap)
        detector.close()

        if (boxes.isEmpty()) {
            return imageBytes
        }

        val ocr = TextOcr(ocrRecFile, ocrKeysFile)
        val engine = getEngine()
        val sourceLang = preferences.sourceLanguage.get()
        val targetLang = preferences.targetLanguage.get()

        val redrawItems = mutableListOf<CanvasRedrawer.RedrawItem>()
        for (box in boxes) {
            val rect = box.rect
            val cropX = rect.left.toInt().coerceIn(0, originalBitmap.width - 1)
            val cropY = rect.top.toInt().coerceIn(0, originalBitmap.height - 1)
            val cropW = rect.width().toInt().coerceIn(1, originalBitmap.width - cropX)
            val cropH = rect.height().toInt().coerceIn(1, originalBitmap.height - cropY)

            val cropped = Bitmap.createBitmap(originalBitmap, cropX, cropY, cropW, cropH)
            val originalText = ocr.recognize(cropped)
            val translatedText = if (originalText.isNotBlank()) {
                try {
                    engine.translate(originalText, sourceLang, targetLang)
                } catch (e: Exception) {
                    originalText
                }
            } else {
                ""
            }

            redrawItems.add(CanvasRedrawer.RedrawItem(box, originalText, translatedText))
        }
        ocr.close()

        val inpainter = AotInpainter(inpaintFile)
        val textBoxes = boxes.map { it.rect }
        val inpaintedBitmap = inpainter.inpaint(originalBitmap, textBoxes)
        inpainter.close()

        val finalBitmap = redrawer.redraw(inpaintedBitmap, redrawItems)

        val outputStream = java.io.ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }

    private fun getEngine(): TranslationEngineProvider {
        return when (preferences.translationEngine.get()) {
            TranslationEngine.MLKIT -> MlKitTranslatorEngine()
            TranslationEngine.GOOGLE -> GoogleTranslatorEngine(client)
            TranslationEngine.GEMINI -> GeminiTranslatorEngine(
                client = client,
                apiKey = preferences.geminiApiKey.get(),
                modelName = preferences.geminiModel.get(),
            )
            TranslationEngine.OPENROUTER -> OpenRouterTranslatorEngine(
                client = client,
                apiKey = preferences.openRouterApiKey.get(),
                modelName = preferences.openRouterModel.get(),
            )
        }
    }
}
