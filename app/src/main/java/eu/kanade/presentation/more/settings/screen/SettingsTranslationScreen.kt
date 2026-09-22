package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.TranslationModelManager
import tachiyomi.domain.translation.service.TranslationPreferences
import tachiyomi.presentation.core.util.collectAsState

@Inject
@SingleIn(AppScope::class)
class SettingsTranslationScreen(
    private val translationPreferences: TranslationPreferences,
    private val modelManager: TranslationModelManager,
) : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = tachiyomi.i18n.MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val readerFontPref = translationPreferences.readerFont
        val translateFromPref = translationPreferences.translateFrom
        val translateToPref = translationPreferences.translateTo
        val enginePref = translationPreferences.translatorEngine
        val geminiApiKeyPref = translationPreferences.geminiApiKey
        val geminiModelPref = translationPreferences.geminiModel
        val openRouterApiKeyPref = translationPreferences.openRouterApiKey
        val openRouterModelPref = translationPreferences.openRouterModel
        val autoTranslatePref = translationPreferences.autoTranslateAfterDownload

        val engine by enginePref.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = "General Settings",
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = readerFontPref,
                        title = "Reader Font",
                        subtitle = "%s",
                        entries = mapOf(
                            TranslationPreferences.FONT_ANIME_ACE to "Anime Ace",
                            TranslationPreferences.FONT_CC_WILD_WORDS to "CC Wild Words",
                            TranslationPreferences.FONT_KOMIKA_AXIS to "Komika Axis",
                            TranslationPreferences.FONT_BANGERS to "Bangers",
                            TranslationPreferences.FONT_COMIC_NEUE to "Comic Neue",
                        ),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Download Font",
                        subtitle = "Download chosen comic font",
                        onClick = {
                            modelManager.downloadFont(readerFontPref.get())
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translateFromPref,
                        title = "Translate From",
                        subtitle = "%s",
                        entries = mapOf(
                            "English" to "English",
                            "China" to "Chinese",
                            "Jepang" to "Japanese",
                            "Korea" to "Korean",
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translateToPref,
                        title = "Translate To",
                        subtitle = "%s",
                        entries = mapOf(
                            "Indonesian" to "Indonesian",
                            "English" to "English",
                            "Spanish" to "Spanish",
                            "French" to "French",
                        ),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = autoTranslatePref,
                        title = "Auto-translate after download",
                        subtitle = "Automatically queue chapter for translation when download completes",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Translator Engine",
                preferenceItems = buildList {
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = enginePref,
                            title = "Translator Type",
                            subtitle = "%s",
                            entries = mapOf(
                                TranslationPreferences.ENGINE_MLKIT to "ML-Kit Translate (Offline)",
                                TranslationPreferences.ENGINE_GOOGLE_TRANSLATE to "Google Translate (Web)",
                                TranslationPreferences.ENGINE_GEMINI to "Gemini AI",
                                TranslationPreferences.ENGINE_OPENROUTER to "OpenRouter",
                            ),
                        ),
                    )

                    if (engine == TranslationPreferences.ENGINE_GEMINI) {
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                preference = geminiApiKeyPref,
                                title = "Gemini API Key",
                                subtitle = "Input Gemini API Key",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                preference = geminiModelPref,
                                title = "Gemini Model ID",
                                subtitle = "Default: gemini-2.5-flash",
                            ),
                        )
                    }

                    if (engine == TranslationPreferences.ENGINE_OPENROUTER) {
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                preference = openRouterApiKeyPref,
                                title = "OpenRouter API Key",
                                subtitle = "Input OpenRouter API Key",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                preference = openRouterModelPref,
                                title = "OpenRouter Model ID",
                                subtitle = "Default: google/gemini-2.5-flash",
                            ),
                        )
                    }
                },
            ),
            Preference.PreferenceGroup(
                title = "ONNX Machine Learning Models",
                preferenceItems = listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = "Download Text Detector Model",
                        subtitle = if (modelManager.isDetectorDownloaded()) {
                            "Downloaded"
                        } else {
                            "Click to download detector-v4-s_int8.onnx"
                        },
                        onClick = {
                            modelManager.downloadModel(
                                TranslationModelManager.DETECTOR_URL,
                                "detector-v4-s_int8.onnx",
                            )
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Download OCR Model",
                        subtitle = if (modelManager.isOcrDownloaded()) {
                            "Downloaded"
                        } else {
                            "Click to download PP-OCRv6_small_rec.onnx"
                        },
                        onClick = {
                            modelManager.downloadModel(
                                TranslationModelManager.OCR_URL,
                                "PP-OCRv6_small_rec.onnx",
                            )
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Download Inpainting Model",
                        subtitle = if (modelManager.isInpaintingDownloaded()) {
                            "Downloaded"
                        } else {
                            "Click to download aot.onnx"
                        },
                        onClick = {
                            modelManager.downloadModel(
                                TranslationModelManager.INPAINTING_URL,
                                "aot.onnx",
                            )
                        },
                    ),
                ),
            ),
        )
    }
}
