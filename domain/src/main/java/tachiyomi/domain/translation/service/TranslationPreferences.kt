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
    val readerFont: Preference<String> = preferenceStore.getString("pref_translation_reader_font", FONT_ANIME_ACE)

    val translateFrom: Preference<String> = preferenceStore.getString("pref_translation_from", "English")

    val translateTo: Preference<String> = preferenceStore.getString("pref_translation_to", "Indonesian")

    val translatorEngine: Preference<String> = preferenceStore.getString("pref_translation_engine", ENGINE_MLKIT)

    val geminiApiKey: Preference<String> = preferenceStore.getString("pref_translation_gemini_api_key", "")

    val geminiModel: Preference<String> = preferenceStore.getString("pref_translation_gemini_model", "gemini-2.5-flash")

    val openRouterApiKey: Preference<String> = preferenceStore.getString("pref_translation_openrouter_api_key", "")

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_model",
        "google/gemini-2.5-flash",
    )

    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download",
        false,
    )

    companion object {
        const val FONT_ANIME_ACE = "Anime Ace"
        const val FONT_CC_WILD_WORDS = "CC Wild Words"
        const val FONT_KOMIKA_AXIS = "Komika Axis"
        const val FONT_BANGERS = "Bangers"
        const val FONT_COMIC_NEUE = "Comic Neue"

        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_GOOGLE_TRANSLATE = "google_translate"
        const val ENGINE_GEMINI = "gemini"
        const val ENGINE_OPENROUTER = "openrouter"
    }
}
