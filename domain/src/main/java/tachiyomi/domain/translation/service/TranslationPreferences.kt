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

    val readerFont: Preference<String> = preferenceStore.getString("pref_translation_reader_font", FONT_WILD_WORDS)

    val translateFrom: Preference<String> = preferenceStore.getString("pref_translation_from", "en")

    val translateTo: Preference<String> = preferenceStore.getString("pref_translation_to", "id")

    val translatorType: Preference<String> = preferenceStore.getString("pref_translation_type", TYPE_GOOGLE)

    val geminiApiKey: Preference<String> = preferenceStore.getString("pref_translation_gemini_api_key", "")

    val geminiModel: Preference<String> = preferenceStore.getString("pref_translation_gemini_model", "gemini-1.5-flash")

    val openRouterApiKey: Preference<String> = preferenceStore.getString("pref_translation_openrouter_api_key", "")

    val openRouterModel: Preference<String> = preferenceStore.getString(
        "pref_translation_openrouter_model",
        "google/gemini-flash-1.5",
    )

    val autoTranslateAfterDownload: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_auto_translate_after_download",
        false,
    )

    companion object {
        const val FONT_WILD_WORDS = "Wild Words"
        const val FONT_ANIME_ACE = "Anime Ace"
        const val FONT_MANGA_TEMPLE = "Manga Temple"

        const val TYPE_MLKIT = "mlkit"
        const val TYPE_GOOGLE = "google"
        const val TYPE_GEMINI = "gemini"
        const val TYPE_OPENROUTER = "openrouter"
    }
}
