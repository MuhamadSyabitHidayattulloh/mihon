package eu.kanade.tachiyomi.data.translation.engine

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslationEngine : TranslationEngine {

    override suspend fun translate(texts: List<String>, sourceLang: String, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()

        val srcCode = mapLanguageCode(sourceLang)
        val tgtCode = mapLanguageCode(targetLang)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(srcCode)
            .setTargetLanguage(tgtCode)
            .build()

        val translator = Translation.getClient(options)

        return try {
            // Ensure model is downloaded
            suspendCancellableCoroutine<Unit> { cont ->
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }

            texts.map { text ->
                if (text.isBlank()) {
                    text
                } else {
                    suspendCancellableCoroutine { cont ->
                        translator.translate(text)
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resumeWithException(it) }
                    }
                }
            }
        } finally {
            translator.close()
        }
    }

    private fun mapLanguageCode(code: String): String {
        return when (code.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "zh", "cn", "zh-cn" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "id" -> TranslateLanguage.INDONESIAN
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "ru" -> TranslateLanguage.RUSSIAN
            "pt", "pt-br" -> TranslateLanguage.PORTUGUESE
            "it" -> TranslateLanguage.ITALIAN
            else -> TranslateLanguage.fromLanguageTag(code) ?: TranslateLanguage.ENGLISH
        }
    }
}
