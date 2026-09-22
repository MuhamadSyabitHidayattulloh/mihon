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

    val sourceLanguage: Preference<String> = preferenceStore.getString(
        "pref_translation_source_lang_key",
        "en",
    )

    val targetLanguage: Preference<String> = preferenceStore.getString(
        "pref_translation_target_lang_key",
        "id",
    )

    val translatorType: Preference<String> = preferenceStore.getString(
        "pref_translator_type_key",
        "mlkit",
    )

    val geminiApiKey: Preference<String> = preferenceStore.getString(
        "pref_gemini_api_key_key",
        "",
    )

    val geminiModel: Preference<String> = preferenceStore.getString(
        "pref_gemini_model_key",
        "gemini-1.5-flash",
    )

    val openRouterApiKey: Preference<String> = preferenceStore.getString(
        "pref_openrouter_api_key_key",
        "",
    )

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_openrouter_model_key",
        "google/gemini-2.5-flash",
    )

    val showTranslated: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_show_translated_images_key",
        true,
    )
}
