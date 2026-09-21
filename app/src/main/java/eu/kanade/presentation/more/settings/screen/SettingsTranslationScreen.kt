package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val translationPreferences = remember { context.appGraph.translationPreferences }

        val sourceLang by translationPreferences.sourceLanguage.collectAsState()
        val targetLang by translationPreferences.targetLanguage.collectAsState()
        val engine by translationPreferences.translatorEngine.collectAsState()
        val font by translationPreferences.readerFont.collectAsState()

        val languages = mapOf(
            "auto" to "Auto Detect",
            "ja" to "Japanese",
            "zh" to "Chinese",
            "en" to "English",
            "id" to "Indonesian",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "ko" to "Korean",
            "ru" to "Russian",
        )

        val engines = mapOf(
            "mlkit" to "ML Kit Translate",
            "google" to "Google Translate",
            "gemini" to "Gemini",
            "openrouter" to "OpenRouter",
        )

        val fonts = mapOf(
            "manga_temple" to "Manga Temple (Comic)",
            "anime_ace" to "Anime Ace (Comic)",
            "bangers" to "Bangers (Comic)",
        )

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_translations),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.sourceLanguage,
                        entries = languages,
                        title = stringResource(MR.strings.pref_translation_source_lang),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.targetLanguage,
                        entries = languages.filterKeys { it != "auto" },
                        title = stringResource(MR.strings.pref_translation_target_lang),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.translatorEngine,
                        entries = engines,
                        title = stringResource(MR.strings.pref_translation_engine),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.readerFont,
                        entries = fonts,
                        title = stringResource(MR.strings.pref_translation_reader_font),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = translationPreferences.autoTranslateAfterDownload,
                        title = stringResource(MR.strings.pref_auto_translate_after_download),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Gemini Settings",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiApiKey,
                        title = stringResource(MR.strings.pref_translation_gemini_api_key),
                        subtitle = if (translationPreferences.geminiApiKey.get().isBlank()) {
                            "Not set"
                        } else {
                            "••••••••"
                        },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiModel,
                        title = stringResource(MR.strings.pref_translation_gemini_model),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "OpenRouter Settings",
                preferenceItems = listOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openRouterApiKey,
                        title = stringResource(MR.strings.pref_translation_openrouter_api_key),
                        subtitle = if (translationPreferences.openRouterApiKey.get().isBlank()) {
                            "Not set"
                        } else {
                            "••••••••"
                        },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openRouterModel,
                        title = stringResource(MR.strings.pref_translation_openrouter_model),
                    ),
                ),
            ),
        )
    }
}
