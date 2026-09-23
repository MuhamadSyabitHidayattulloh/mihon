package eu.kanade.tachiyomi.data.translation.engine

import tachiyomi.domain.translation.model.TranslationLanguage

interface TranslationEngineProvider {
    suspend fun translate(text: String, sourceLang: TranslationLanguage, targetLang: TranslationLanguage): String
}
