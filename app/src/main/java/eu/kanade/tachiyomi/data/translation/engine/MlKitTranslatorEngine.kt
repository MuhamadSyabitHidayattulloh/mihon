package eu.kanade.tachiyomi.data.translation.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.domain.translation.model.TranslationLanguage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslatorEngine : TranslationEngineProvider {

    override suspend fun translate(
        text: String,
        sourceLang: TranslationLanguage,
        targetLang: TranslationLanguage,
    ): String {
        if (text.isBlank()) return text

        val sourceMl = mapLanguage(sourceLang)
        val targetMl = mapLanguage(targetLang)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceMl)
            .setTargetLanguage(targetMl)
            .build()

        val translator = Translation.getClient(options)
        return try {
            val conditions = DownloadConditions.Builder().build()
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            suspendCancellableCoroutine { cont ->
                translator.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            translator.close()
        }
    }

    private fun mapLanguage(language: TranslationLanguage): String {
        return when (language) {
            TranslationLanguage.ENGLISH -> TranslateLanguage.ENGLISH
            TranslationLanguage.CHINESE -> TranslateLanguage.CHINESE
            TranslationLanguage.JAPANESE -> TranslateLanguage.JAPANESE
            TranslationLanguage.KOREAN -> TranslateLanguage.KOREAN
            TranslationLanguage.INDONESIAN -> TranslateLanguage.INDONESIAN
            TranslationLanguage.SPANISH -> TranslateLanguage.SPANISH
            TranslationLanguage.FRENCH -> TranslateLanguage.FRENCH
            TranslationLanguage.GERMAN -> TranslateLanguage.GERMAN
            TranslationLanguage.RUSSIAN -> TranslateLanguage.RUSSIAN
            TranslationLanguage.PORTUGUESE -> TranslateLanguage.PORTUGUESE
            TranslationLanguage.ITALIAN -> TranslateLanguage.ITALIAN
            TranslationLanguage.THAI -> TranslateLanguage.THAI
            TranslationLanguage.VIETNAMESE -> TranslateLanguage.VIETNAMESE
            TranslationLanguage.ARABIC -> TranslateLanguage.ARABIC
        }
    }
}
