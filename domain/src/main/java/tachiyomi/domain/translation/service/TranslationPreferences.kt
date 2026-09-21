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
    val readerFont: Preference<String> = preferenceStore.getString("pref_translation_reader_font_key", "Wild Words")

    val translateFrom: Preference<String> = preferenceStore.getString("pref_translation_from_key", "ja")

    val translateTo: Preference<String> = preferenceStore.getString("pref_translation_to_key", "id")

    val translatorEngine: Preference<String> = preferenceStore.getString(
        "pref_translation_engine_key",
        ENGINE_GOOGLE,
    )

    val openRouterApiKey: Preference<String> = preferenceStore.getString("pref_translation_openrouter_api_key", "")

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_model",
        "google/gemini-2.5-flash",
    )

    val geminiApiKey: Preference<String> = preferenceStore.getString("pref_translation_gemini_api_key", "")

    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download",
        false,
    )

    companion object {
        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_GOOGLE = "google"
        const val ENGINE_GEMINI = "gemini"
        const val ENGINE_OPENROUTER = "openrouter"
    }
}
