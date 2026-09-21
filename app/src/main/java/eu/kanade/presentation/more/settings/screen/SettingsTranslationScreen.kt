package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val translationPreferences = remember { context.appGraph.translationPreferences }

        val translatorType by translationPreferences.translatorType.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_general_group),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.readerFont,
                        entries = mapOf(
                            TranslationPreferences.FONT_WILD_WORDS to TranslationPreferences.FONT_WILD_WORDS,
                            TranslationPreferences.FONT_ANIME_ACE to TranslationPreferences.FONT_ANIME_ACE,
                            TranslationPreferences.FONT_MANGA_TEMPLE to TranslationPreferences.FONT_MANGA_TEMPLE,
                        ),
                        title = stringResource(MR.strings.pref_translation_reader_font),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.translateFrom,
                        entries = mapOf(
                            "en" to "English",
                            "zh" to "Chinese",
                            "ja" to "Japanese",
                        ),
                        title = stringResource(MR.strings.pref_translation_from),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.translateTo,
                        entries = mapOf(
                            "id" to "Indonesian",
                            "en" to "English",
                            "es" to "Spanish",
                            "fr" to "French",
                            "de" to "German",
                            "pt" to "Portuguese",
                            "ru" to "Russian",
                        ),
                        title = stringResource(MR.strings.pref_translation_to),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = translationPreferences.autoTranslateAfterDownload,
                        title = stringResource(MR.strings.pref_auto_translate_after_download),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_engine_group),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.translatorType,
                        entries = mapOf(
                            TranslationPreferences.TYPE_MLKIT to "ML-Kit Translate (Offline)",
                            TranslationPreferences.TYPE_GOOGLE to "Google Translate (Web)",
                            TranslationPreferences.TYPE_GEMINI to "Gemini AI",
                            TranslationPreferences.TYPE_OPENROUTER to "OpenRouter API",
                        ),
                        title = stringResource(MR.strings.pref_translator_type),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiApiKey,
                        title = stringResource(MR.strings.pref_translation_gemini_api_key),
                        subtitle = if (translationPreferences.geminiApiKey.get().isBlank()) {
                            stringResource(MR.strings.not_set)
                        } else {
                            "••••••••"
                        },
                        enabled = translatorType == TranslationPreferences.TYPE_GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiModel,
                        title = stringResource(MR.strings.pref_translation_gemini_model),
                        subtitle = translationPreferences.geminiModel.get(),
                        enabled = translatorType == TranslationPreferences.TYPE_GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openRouterApiKey,
                        title = stringResource(MR.strings.pref_translation_openrouter_api_key),
                        subtitle = if (translationPreferences.openRouterApiKey.get().isBlank()) {
                            stringResource(MR.strings.not_set)
                        } else {
                            "••••••••"
                        },
                        enabled = translatorType == TranslationPreferences.TYPE_OPENROUTER,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openRouterModel,
                        title = stringResource(MR.strings.pref_translation_openrouter_model),
                        subtitle = translationPreferences.openRouterModel.get(),
                        enabled = translatorType == TranslationPreferences.TYPE_OPENROUTER,
                    ),
                ),
            ),
        )
    }
}
