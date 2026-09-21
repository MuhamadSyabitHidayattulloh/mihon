package tachiyomi.domain.translation.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    preferenceStore: PreferenceStore,
) {
    val sourceLanguage: Preference<String> = preferenceStore.getString("pref_translation_source_lang", "ja")
    val targetLanguage: Preference<String> = preferenceStore.getString("pref_translation_target_lang", "en")
    val translatorEngine: Preference<String> = preferenceStore.getString("pref_translation_engine", "google")

    val geminiApiKey: Preference<String> = preferenceStore.getString("pref_translation_gemini_api_key", "")
    val geminiModel: Preference<String> = preferenceStore.getString("pref_translation_gemini_model", "gemini-1.5-flash")

    val openRouterApiKey: Preference<String> = preferenceStore.getString("pref_translation_openrouter_api_key", "")
    val openRouterModel: Preference<String> = preferenceStore.getString("pref_translation_openrouter_model", "google/gemini-2.5-flash")

    val readerFont: Preference<String> = preferenceStore.getString("pref_translation_reader_font", "manga_temple")
    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean("pref_auto_translate_after_download", false)
}
