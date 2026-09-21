package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import mihon.app.di.appGraph
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.presentation.core.util.collectAsState

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): dev.icerock.moko.resources.StringResource {
        // Return a StringResource or string
        return tachiyomi.i18n.MR.strings.label_settings
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { context.appGraph.translationPreferences }
        val engine by preferences.translatorEngine.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = "Pengaturan Terjemahkan",
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.readerFont,
                        entries = mapOf(
                            "Wild Words" to "Wild Words",
                            "Anime Ace" to "Anime Ace",
                            "Manga Temple" to "Manga Temple",
                        ),
                        title = "Reader Font",
                        subtitle = "Font komik untuk merender teks terjemahan",
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.translateFrom,
                        entries = mapOf(
                            "ja" to "Jepang (Japanese)",
                            "zh" to "China (Chinese)",
                            "en" to "English",
                        ),
                        title = "Translate From",
                        subtitle = "Bahasa asal komik",
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.translateTo,
                        entries = mapOf(
                            "id" to "Indonesia",
                            "en" to "English",
                            "es" to "Español",
                            "pt" to "Português",
                            "fr" to "Français",
                        ),
                        title = "Translate To",
                        subtitle = "Bahasa target terjemahan",
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.translatorEngine,
                        entries = mapOf(
                            TranslationPreferences.ENGINE_MLKIT to "ML-Kit Translate (Offline)",
                            TranslationPreferences.ENGINE_GOOGLE to "Google Translate (Web based)",
                            TranslationPreferences.ENGINE_GEMINI to "Gemini AI",
                            TranslationPreferences.ENGINE_OPENROUTER to "OpenRouter",
                        ),
                        title = "Tipe Translator",
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.geminiApiKey,
                        title = "Gemini API Key",
                        enabled = engine == TranslationPreferences.ENGINE_GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.openRouterApiKey,
                        title = "OpenRouter API Key",
                        enabled = engine == TranslationPreferences.ENGINE_OPENROUTER,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.openRouterModel,
                        title = "OpenRouter Model",
                        subtitle = "Contoh: google/gemini-2.5-flash",
                        enabled = engine == TranslationPreferences.ENGINE_OPENROUTER,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.autoTranslateAfterDownload,
                        title = "Terjemahkan Setelah Mendownload Chapter",
                        subtitle = "Otomatis jalankan terjemahan ketika chapter selesai diunduh",
                    ),
                ),
            ),
        )
    }
}
