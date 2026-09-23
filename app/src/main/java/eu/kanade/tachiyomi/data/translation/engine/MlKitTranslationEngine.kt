package eu.kanade.tachiyomi.data.translation.engine

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class MlKitTranslationEngine : TranslationEngine {

    private fun mapLanguageCode(code: String): String? {
        return when (code.lowercase()) {
            "en" -> TranslateLanguage.ENGLISH
            "zh" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "id" -> TranslateLanguage.INDONESIAN
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "ru" -> TranslateLanguage.RUSSIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "it" -> TranslateLanguage.ITALIAN
            "ar" -> TranslateLanguage.ARABIC
            "th" -> TranslateLanguage.THAI
            "vi" -> TranslateLanguage.VIETNAMESE
            else -> TranslateLanguage.fromLanguageTag(code)
        }
    }

    override suspend fun translate(texts: List<String>, fromLang: String, toLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()

        val sourceLang = mapLanguageCode(fromLang) ?: TranslateLanguage.ENGLISH
        val targetLang = mapLanguageCode(toLang) ?: TranslateLanguage.INDONESIAN

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()

        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded().await()
            val results = mutableListOf<String>()
            for (text in texts) {
                if (text.isBlank()) {
                    results.add("")
                } else {
                    val translated = translator.translate(text).await()
                    results.add(translated)
                }
            }
            return results
        } finally {
            translator.close()
        }
    }
}
