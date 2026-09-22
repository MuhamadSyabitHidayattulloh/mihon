package eu.kanade.domain.translation.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download_key",
        false,
    )

    val translateFrom: Preference<String> = preferenceStore.getString(
        "pref_translate_from_key",
        "en",
    )

    val translateTo: Preference<String> = preferenceStore.getString(
        "pref_translate_to_key",
        "id",
    )

    val translatorType: Preference<String> = preferenceStore.getString(
        "pref_translator_type_key",
        TRANSLATOR_MLKIT,
    )

    val geminiApiKey: Preference<String> = preferenceStore.getString(
        "pref_gemini_api_key",
        "",
    )

    val geminiModel: Preference<String> = preferenceStore.getString(
        "pref_gemini_model",
        "gemini-2.5-flash",
    )

    val openRouterApiKey: Preference<String> = preferenceStore.getString(
        "pref_openrouter_api_key",
        "",
    )

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_openrouter_model",
        "google/gemini-2.5-flash",
    )

    companion object {
        const val TRANSLATOR_MLKIT = "mlkit"
        const val TRANSLATOR_GOOGLE = "google"
        const val TRANSLATOR_GEMINI = "gemini"
        const val TRANSLATOR_OPENROUTER = "openrouter"
    }
}
