package tachiyomi.domain.translation.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.model.TranslationLanguage

@Inject
@SingleIn(AppScope::class)
class TranslationPreferences(
    preferenceStore: PreferenceStore,
) {

    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download",
        false,
    )

    val sourceLanguage: Preference<TranslationLanguage> = preferenceStore.getEnum(
        "pref_translation_source_lang",
        TranslationLanguage.JAPANESE,
    )

    val targetLanguage: Preference<TranslationLanguage> = preferenceStore.getEnum(
        "pref_translation_target_lang",
        TranslationLanguage.INDONESIAN,
    )

    val translationEngine: Preference<TranslationEngine> = preferenceStore.getEnum(
        "pref_translation_engine",
        TranslationEngine.MLKIT,
    )

    val geminiApiKey: Preference<String> = preferenceStore.getString(
        "pref_gemini_api_key",
        "",
    )

    val geminiModel: Preference<String> = preferenceStore.getString(
        "pref_gemini_model",
        "gemini-1.5-flash",
    )

    val openRouterApiKey: Preference<String> = preferenceStore.getString(
        "pref_openrouter_api_key",
        "",
    )

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_openrouter_model",
        "google/gemini-flash-1.5",
    )

    val detectorModelDownloaded: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_detector_model_downloaded",
        false,
    )

    val ocrModelDownloaded: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_ocr_model_downloaded",
        false,
    )

    val inpaintingModelDownloaded: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_inpainting_model_downloaded",
        false,
    )
}
