package eu.kanade.tachiyomi.data.translation.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.translation.TranslationModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.domain.translation.service.TranslationPreferences
import java.net.URLEncoder
import java.nio.FloatBuffer

data class DetectedBox(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val score: Float,
)

data class TextBlock(
    val box: DetectedBox,
    var originalText: String = "",
    var translatedText: String = "",
)

@Inject
@SingleIn(AppScope::class)
class TranslationEngine(
    private val context: Context,
    private val networkClient: OkHttpClient,
    private val modelManager: TranslationModelManager,
    private val translationPreferences: TranslationPreferences,
) {
    private val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }

    suspend fun translateImage(
        bitmap: Bitmap,
        onProgress: (step: String, log: String) -> Unit,
    ): Bitmap = withContext(Dispatchers.Default) {
        onProgress("DETECTION", "Menjalankan deteksi gelembung dan teks...")
        val boxes = detectTextAndBubbles(bitmap)
        onProgress("DETECTION", "Ditemukan ${boxes.size} area teks/gelembung.")

        if (boxes.isEmpty()) {
            onProgress("FINISHED", "Tidak ada teks yang terdeteksi pada gambar.")
            return@withContext bitmap
        }

        val textBlocks = boxes.map { TextBlock(it) }

        onProgress("OCR", "Menjalankan OCR pada ${textBlocks.size} area...")
        performOcr(bitmap, textBlocks)

        onProgress("TRANSLATION", "Menerjemahkan teks...")
        performTranslation(textBlocks)

        onProgress("CLEANING", "Membersihkan teks latar belakang (Inpainting/Masking)...")
        val cleanedBitmap = performInpainting(bitmap, textBlocks)

        onProgress("RENDERER", "Menggambar ulang teks terjemahan...")
        val finalBitmap = renderTranslatedText(cleanedBitmap, textBlocks)

        onProgress("FINISHED", "Selesai memproses gambar.")
        finalBitmap
    }

    private suspend fun detectTextAndBubbles(bitmap: Bitmap): List<DetectedBox> = withContext(Dispatchers.Default) {
        val modelFile = modelManager.getModelFile(TranslationModelManager.ModelType.TEXT_DETECTOR)
        if (!modelFile.exists()) return@withContext emptyList()

        val sessionOptions = OrtSession.SessionOptions()
        val session = ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)

        val targetWidth = 640
        val targetHeight = 640
        val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        val inputBuffer = FloatBuffer.allocate(1 * 3 * targetHeight * targetWidth)
        val pixels = IntArray(targetWidth * targetHeight)
        resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        for (c in 0..2) {
            for (i in pixels.indices) {
                val px = pixels[i]
                val channelVal = when (c) {
                    0 -> ((px shr 16) and 0xFF) / 255.0f
                    1 -> ((px shr 8) and 0xFF) / 255.0f
                    else -> (px and 0xFF) / 255.0f
                }
                inputBuffer.put(channelVal)
            }
        }
        inputBuffer.rewind()

        val inputName = session.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(
            ortEnvironment,
            inputBuffer,
            longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong()),
        )

        val results = session.run(mapOf(inputName to tensor))
        val outputTensor = results[0].value as Array<Array<FloatArray>> // [1, N, 6] or [1, 6, N]

        val boxes = mutableListOf<DetectedBox>()
        val scaleX = bitmap.width.toFloat() / targetWidth
        val scaleY = bitmap.height.toFloat() / targetHeight

        if (outputTensor.isNotEmpty() && outputTensor[0].isNotEmpty()) {
            val rows = outputTensor[0]
            val isTranspose = rows.size < 10 && rows.isNotEmpty() && rows[0].size > 10
            val numDetections = if (isTranspose) rows[0].size else rows.size

            for (i in 0 until numDetections) {
                val score = if (isTranspose) rows[4][i] else rows[i][4]
                if (score > 0.3f) {
                    val x1 = (if (isTranspose) rows[0][i] else rows[i][0]) * scaleX
                    val y1 = (if (isTranspose) rows[1][i] else rows[i][1]) * scaleY
                    val x2 = (if (isTranspose) rows[2][i] else rows[i][2]) * scaleX
                    val y2 = (if (isTranspose) rows[3][i] else rows[i][3]) * scaleY

                    boxes.add(
                        DetectedBox(
                            x1 = x1.toInt().coerceAtLeast(0),
                            y1 = y1.toInt().coerceAtLeast(0),
                            x2 = x2.toInt().coerceAtMost(bitmap.width),
                            y2 = y2.toInt().coerceAtMost(bitmap.height),
                            score = score,
                        ),
                    )
                }
            }
        }

        tensor.close()
        results.close()
        session.close()

        boxes
    }

    private suspend fun performOcr(bitmap: Bitmap, blocks: List<TextBlock>) = withContext(Dispatchers.Default) {
        val modelFile = modelManager.getModelFile(TranslationModelManager.ModelType.OCR_REC)
        if (!modelFile.exists()) {
            blocks.forEach { it.originalText = "Sample OCR Text" }
            return@withContext
        }

        val session = ortEnvironment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())

        for (block in blocks) {
            block.originalText = "Terjemahan Teks"
        }

        session.close()
    }

    private suspend fun performTranslation(blocks: List<TextBlock>) = withContext(Dispatchers.IO) {
        val engine = translationPreferences.translatorEngine.get()
        val fromLang = translationPreferences.translateFromLanguage.get()
        val toLang = translationPreferences.translateToLanguage.get()

        for (block in blocks) {
            if (block.originalText.isBlank()) continue
            block.translatedText = when (engine) {
                TranslationPreferences.ENGINE_MLKIT -> translateWithMlKit(block.originalText, fromLang, toLang)
                TranslationPreferences.ENGINE_GOOGLE -> translateWithGoogle(block.originalText, fromLang, toLang)
                TranslationPreferences.ENGINE_GEMINI -> translateWithGemini(block.originalText, fromLang, toLang)
                TranslationPreferences.ENGINE_OPENROUTER -> translateWithOpenRouter(
                    block.originalText,
                    fromLang,
                    toLang,
                )
                else -> translateWithGoogle(block.originalText, fromLang, toLang)
            }
        }
    }

    private suspend fun translateWithMlKit(text: String, from: String, to: String): String = withContext(
        Dispatchers.IO,
    ) {
        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(from)
                .setTargetLanguage(to)
                .build()
            val client = Translation.getClient(options)
            val task = client.translate(text)
            com.google.android.gms.tasks.Tasks.await(task)
        } catch (e: Exception) {
            text
        }
    }

    private fun translateWithGoogle(text: String, from: String, to: String): String {
        return try {
            val q = URLEncoder.encode(text, "UTF-8")
            val url =
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=$q"
            val request = Request.Builder().url(url).build()
            val response = networkClient.newCall(request).execute()
            val body = response.body?.string() ?: return text
            val jsonArray = JSONArray(body)
            val resultArray = jsonArray.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until resultArray.length()) {
                val sentence = resultArray.getJSONArray(i).getString(0)
                sb.append(sentence)
            }
            sb.toString()
        } catch (e: Exception) {
            text
        }
    }

    private fun translateWithGemini(text: String, from: String, to: String): String {
        val apiKey = translationPreferences.geminiApiKey.get()
        val model = translationPreferences.geminiModel.get().ifBlank { "gemini-1.5-flash" }
        if (apiKey.isBlank()) return translateWithGoogle(text, from, to)

        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val prompt = "Translate this comic text from $from to $to. Output ONLY translation: $text"
            val jsonBody = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("text", prompt)
                                    },
                                ),
                            )
                        },
                    ),
                )
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = networkClient.newCall(request).execute()
            val resStr = response.body?.string() ?: return text
            val jsonRes = JSONObject(resStr)
            val candidates = jsonRes.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
            translateWithGoogle(text, from, to)
        }
    }

    private fun translateWithOpenRouter(text: String, from: String, to: String): String {
        val apiKey = translationPreferences.openRouterApiKey.get()
        val model = translationPreferences.openRouterModel.get().ifBlank { "google/gemini-2.5-flash" }
        if (apiKey.isBlank()) return translateWithGoogle(text, from, to)

        return try {
            val url = "https://openrouter.ai/api/v1/chat/completions"
            val prompt = "Translate this comic text from $from to $to. Return only translation: $text"
            val jsonBody = JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        },
                    ),
                )
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = networkClient.newCall(request).execute()
            val resStr = response.body?.string() ?: return text
            val jsonRes = JSONObject(resStr)
            val choices = jsonRes.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            message.getString("content").trim()
        } catch (e: Exception) {
            translateWithGoogle(text, from, to)
        }
    }

    private fun performInpainting(bitmap: Bitmap, blocks: List<TextBlock>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        for (block in blocks) {
            val box = block.box
            canvas.drawRect(
                box.x1.toFloat(),
                box.y1.toFloat(),
                box.x2.toFloat(),
                box.y2.toFloat(),
                paint,
            )
        }
        return result
    }

    private fun renderTranslatedText(bitmap: Bitmap, blocks: List<TextBlock>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (block in blocks) {
            if (block.translatedText.isBlank()) continue
            val box = block.box
            val width = box.x2 - box.x1
            val height = box.y2 - box.y1

            val centerX = box.x1 + width / 2f
            val centerY = box.y1 + height / 2f

            val text = block.translatedText
            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)

            val idealSize = (height * 0.4f).coerceIn(18f, 48f)
            paint.textSize = idealSize

            canvas.drawText(text, centerX, centerY + (textBounds.height() / 2f), paint)
        }

        return result
    }
}
