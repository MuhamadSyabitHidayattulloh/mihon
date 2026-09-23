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
    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download_key",
        false,
    )

    val translateFrom: Preference<String> = preferenceStore.getString(
        "pref_translate_from_key",
        "en", // Default English ("en", "zh", "ja", "ko")
    )

    val translateTo: Preference<String> = preferenceStore.getString(
        "pref_translate_to_key",
        "id", // Default Indonesian or app language
    )

    val translatorEngine: Preference<String> = preferenceStore.getString(
        "pref_translator_engine_key",
        ENGINE_MLKIT,
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
        "google/gemini-flash-1.5",
    )

    companion object {
        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_GOOGLE_WEB = "google_web"
        const val ENGINE_GEMINI = "gemini"
        const val ENGINE_OPENROUTER = "openrouter"
    }
}
