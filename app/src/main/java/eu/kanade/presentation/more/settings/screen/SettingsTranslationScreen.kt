package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.ModelDownloadState
import eu.kanade.tachiyomi.data.translation.TranslationModelManager
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences = remember { context.appGraph.translationPreferences }
        val modelManager = remember { context.appGraph.translationModelManager }

        val translatorType by preferences.translatorType.collectAsState()
        val sourceLang by preferences.sourceLanguage.collectAsState()
        val targetLang by preferences.targetLanguage.collectAsState()
        val modelStates by modelManager.modelStates.collectAsState()

        val mainGroup = Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_translation),
            preferenceItems = buildList {
                add(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = preferences.autoTranslateAfterDownload,
                        title = stringResource(MR.strings.pref_auto_translate),
                        subtitle = stringResource(MR.strings.pref_auto_translate_summary),
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.sourceLanguage,
                        entries = mapOf(
                            "en" to "English",
                            "zh" to "Chinese (China)",
                            "ja" to "Japanese",
                            "ko" to "Korean",
                        ),
                        title = stringResource(MR.strings.pref_translate_source_lang),
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.targetLanguage,
                        entries = mapOf(
                            "id" to "Indonesian",
                            "en" to "English",
                            "es" to "Spanish",
                            "ja" to "Japanese",
                            "zh" to "Chinese",
                            "ko" to "Korean",
                            "fr" to "French",
                            "de" to "German",
                            "ru" to "Russian",
                            "pt" to "Portuguese",
                        ),
                        title = stringResource(MR.strings.pref_translate_target_lang),
                    ),
                )
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.translatorType,
                        entries = mapOf(
                            "mlkit" to stringResource(MR.strings.pref_translator_type_mlkit),
                            "google" to stringResource(MR.strings.pref_translator_type_google),
                            "gemini" to stringResource(MR.strings.pref_translator_type_gemini),
                            "openrouter" to stringResource(MR.strings.pref_translator_type_openrouter),
                        ),
                        title = stringResource(MR.strings.pref_translator_type),
                    ),
                )

                if (translatorType == "gemini") {
                    add(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = preferences.geminiApiKey,
                            title = stringResource(MR.strings.pref_gemini_api_key),
                        ),
                    )
                    add(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = preferences.geminiModel,
                            title = stringResource(MR.strings.pref_gemini_model),
                        ),
                    )
                }

                if (translatorType == "openrouter") {
                    add(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = preferences.openRouterApiKey,
                            title = stringResource(MR.strings.pref_openrouter_api_key),
                        ),
                    )
                    add(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = preferences.openRouterModel,
                            title = stringResource(MR.strings.pref_openrouter_model),
                        ),
                    )
                }
            },
        )

        val modelsGroup = Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_translation_models),
            preferenceItems = listOf(
                createModelPreference(
                    key = TranslationModelManager.KEY_DETECTOR,
                    title = "Text & Bubble Detector (detector-v4-s_int8.onnx)",
                    state = modelStates[TranslationModelManager.KEY_DETECTOR] ?: ModelDownloadState.NotDownloaded,
                    modelManager = modelManager,
                ),
                createModelPreference(
                    key = TranslationModelManager.KEY_OCR,
                    title = "OCR Recognizer (PP-OCRv6_small_rec.onnx)",
                    state = modelStates[TranslationModelManager.KEY_OCR] ?: ModelDownloadState.NotDownloaded,
                    modelManager = modelManager,
                ),
                createModelPreference(
                    key = TranslationModelManager.KEY_INPAINTING,
                    title = "Image Cleaner (aot.onnx)",
                    state = modelStates[TranslationModelManager.KEY_INPAINTING] ?: ModelDownloadState.NotDownloaded,
                    modelManager = modelManager,
                ),
                createModelPreference(
                    key = sourceLang,
                    title = "ML-Kit Source Model ($sourceLang)",
                    state = modelStates[sourceLang] ?: ModelDownloadState.NotDownloaded,
                    modelManager = modelManager,
                ),
                createModelPreference(
                    key = targetLang,
                    title = "ML-Kit Target Model ($targetLang)",
                    state = modelStates[targetLang] ?: ModelDownloadState.NotDownloaded,
                    modelManager = modelManager,
                ),
            ),
        )

        return listOf(mainGroup, modelsGroup)
    }

    @Composable
    private fun createModelPreference(
        key: String,
        title: String,
        state: ModelDownloadState,
        modelManager: TranslationModelManager,
    ): Preference.PreferenceItem.TextPreference {
        val subtitle = when (state) {
            is ModelDownloadState.NotDownloaded -> "Not downloaded - Tap to download"
            is ModelDownloadState.Downloading -> "Downloading... (${state.progress}%)"
            is ModelDownloadState.Downloaded -> "Downloaded - Tap to delete"
            is ModelDownloadState.Error -> "Error: ${state.message} - Tap to retry"
        }

        return Preference.PreferenceItem.TextPreference(
            title = title,
            subtitle = subtitle,
            onClick = {
                if (state is ModelDownloadState.Downloaded) {
                    modelManager.deleteModel(key)
                } else if (state !is ModelDownloadState.Downloading) {
                    modelManager.downloadModel(key)
                }
            },
        )
    }
}
