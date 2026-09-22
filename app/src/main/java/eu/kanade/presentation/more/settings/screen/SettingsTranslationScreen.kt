package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.TranslationModelManager
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.service.TranslationPreferences

class SettingsTranslationScreen : SearchableSettings {

    @Inject
    private lateinit var translationPreferences: TranslationPreferences

    @Inject
    private lateinit var modelManager: TranslationModelManager

    @Composable
    override fun getTitleRes() = tachiyomi.i18n.MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val downloadStates by modelManager.downloadState.collectAsState()

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.autoTranslateAfterDownload,
                title = "Unduh terjemahan setelah mendownload chapter",
                subtitle = "Otomatis menerjemahkan chapter yang baru selesai diunduh",
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translateFromLanguage,
                title = "Translate From",
                subtitle = "%s",
                entries = mapOf(
                    "ja" to "Japanese",
                    "en" to "English",
                    "zh" to "Chinese",
                    "ko" to "Korean",
                ),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translateToLanguage,
                title = "Translate To",
                subtitle = "%s",
                entries = mapOf(
                    "id" to "Indonesian",
                    "en" to "English",
                    "es" to "Spanish",
                    "fr" to "French",
                    "de" to "German",
                ),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translatorEngine,
                title = "Tipe Translator",
                subtitle = "%s",
                entries = mapOf(
                    TranslationPreferences.ENGINE_GOOGLE to "Google Translate (Web)",
                    TranslationPreferences.ENGINE_MLKIT to "ML-Kit Translate (Offline)",
                    TranslationPreferences.ENGINE_GEMINI to "Gemini AI",
                    TranslationPreferences.ENGINE_OPENROUTER to "OpenRouter",
                ),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.geminiApiKey,
                title = "Gemini API Key",
                subtitle = "API Key untuk Gemini AI",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.geminiModel,
                title = "Gemini Model Name",
                subtitle = "Default: gemini-1.5-flash",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.openRouterApiKey,
                title = "OpenRouter API Key",
                subtitle = "API Key untuk OpenRouter",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.openRouterModel,
                title = "OpenRouter Model Name",
                subtitle = "Default: google/gemini-2.5-flash",
            ),
            Preference.PreferenceGroup(
                title = "Manajemen Model Terjemahan",
                preferenceItems = TranslationModelManager.ModelType.entries.map { type ->
                    val isDownloaded = modelManager.isModelDownloaded(type)
                    val state = downloadStates[type.name]
                    val subtitleStr = when {
                        state?.isDownloading == true -> "Mengunduh... ${(state.progress * 100).toInt()}%"
                        isDownloaded -> "Model terunduh (Siap digunakan)"
                        state?.error != null -> "Gagal: ${state.error}"
                        else -> "Belum diunduh"
                    }

                    Preference.PreferenceItem.TextPreference(
                        title = type.title,
                        subtitle = subtitleStr,
                        onClick = {
                            scope.launch {
                                if (isDownloaded) {
                                    modelManager.deleteModel(type)
                                } else {
                                    modelManager.downloadModel(type)
                                }
                            }
                        },
                    )
                },
            ),
        )
    }
}
