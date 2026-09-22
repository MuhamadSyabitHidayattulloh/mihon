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

    val readerFont: Preference<String> = preferenceStore.getString(
        "pref_translation_reader_font_key",
        FONT_ANIME_ACE,
    )

    val sourceLanguage: Preference<String> = preferenceStore.getString(
        "pref_translation_source_lang_key",
        LANG_ENGLISH,
    )

    val targetLanguage: Preference<String> = preferenceStore.getString(
        "pref_translation_target_lang_key",
        LANG_INDONESIAN,
    )

    val engineType: Preference<String> = preferenceStore.getString(
        "pref_translation_engine_type_key",
        ENGINE_GOOGLE_TRANSLATE,
    )

    val geminiApiKey: Preference<String> = preferenceStore.getString(
        "pref_translation_gemini_api_key",
        "",
    )

    val geminiModel: Preference<String> = preferenceStore.getString(
        "pref_translation_gemini_model",
        "gemini-1.5-flash",
    )

    val openRouterApiKey: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_api_key",
        "",
    )

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_model",
        "google/gemini-2.0-flash-lite-preview-02-05:free",
    )

    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download_key",
        false,
    )

    val detectionModelDownloaded: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_model_detection_downloaded_key",
        false,
    )

    val ocrModelDownloaded: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_model_ocr_downloaded_key",
        false,
    )

    val inpaintingModelDownloaded: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_model_inpainting_downloaded_key",
        false,
    )

    companion object {
        const val FONT_ANIME_ACE = "Anime Ace"
        const val FONT_CC_WILD_WORDS = "CC Wild Words"
        const val FONT_MANGA_TEMPLE = "Manga Temple"

        const val LANG_ENGLISH = "en"
        const val LANG_CHINESE = "zh"
        const val LANG_JAPANESE = "ja"
        const val LANG_KOREAN = "ko"
        const val LANG_INDONESIAN = "id"

        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_GOOGLE_TRANSLATE = "google_translate"
        const val ENGINE_GEMINI = "gemini"
        const val ENGINE_OPENROUTER = "openrouter"
    }
}
