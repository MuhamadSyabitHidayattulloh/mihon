package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.ai.ModelManager
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.domain.translation.service.TranslationPreferences
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
        val scope = rememberCoroutineScope()
        val translationPreferences = remember { context.appGraph.translationPreferences }
        val modelManager = remember { context.appGraph.modelManager }

        val engineType by translationPreferences.engineType.collectAsState()
        val detectionDownloaded by translationPreferences.detectionModelDownloaded.collectAsState()
        val ocrDownloaded by translationPreferences.ocrModelDownloaded.collectAsState()
        val inpaintingDownloaded by translationPreferences.inpaintingModelDownloaded.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_general_group),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.readerFont,
                        entries = mapOf(
                            TranslationPreferences.FONT_ANIME_ACE to TranslationPreferences.FONT_ANIME_ACE,
                            TranslationPreferences.FONT_CC_WILD_WORDS to TranslationPreferences.FONT_CC_WILD_WORDS,
                            TranslationPreferences.FONT_MANGA_TEMPLE to TranslationPreferences.FONT_MANGA_TEMPLE,
                        ),
                        title = stringResource(MR.strings.pref_translation_reader_font),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.sourceLanguage,
                        entries = mapOf(
                            TranslationPreferences.LANG_ENGLISH to
                                stringResource(MR.strings.pref_translation_lang_english),
                            TranslationPreferences.LANG_CHINESE to
                                stringResource(MR.strings.pref_translation_lang_chinese),
                            TranslationPreferences.LANG_JAPANESE to
                                stringResource(MR.strings.pref_translation_lang_japanese),
                            TranslationPreferences.LANG_KOREAN to
                                stringResource(MR.strings.pref_translation_lang_korean),
                        ),
                        title = stringResource(MR.strings.pref_translation_source_lang),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.targetLanguage,
                        entries = mapOf(
                            TranslationPreferences.LANG_INDONESIAN to
                                stringResource(MR.strings.pref_translation_lang_indonesian),
                            TranslationPreferences.LANG_ENGLISH to
                                stringResource(MR.strings.pref_translation_lang_english),
                            "es" to stringResource(MR.strings.pref_translation_lang_spanish),
                            "fr" to stringResource(MR.strings.pref_translation_lang_french),
                            "de" to stringResource(MR.strings.pref_translation_lang_german),
                            "it" to stringResource(MR.strings.pref_translation_lang_italian),
                            "pt" to stringResource(MR.strings.pref_translation_lang_portuguese),
                            "ru" to stringResource(MR.strings.pref_translation_lang_russian),
                            "vi" to stringResource(MR.strings.pref_translation_lang_vietnamese),
                        ),
                        title = stringResource(MR.strings.pref_translation_target_lang),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = translationPreferences.autoTranslateAfterDownload,
                        title = stringResource(MR.strings.pref_auto_translate_after_download),
                        subtitle = stringResource(MR.strings.pref_auto_translate_after_download_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_engine_group),
                preferenceItems = listOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = translationPreferences.engineType,
                        entries = mapOf(
                            TranslationPreferences.ENGINE_MLKIT to
                                stringResource(MR.strings.pref_translation_engine_mlkit),
                            TranslationPreferences.ENGINE_GOOGLE_TRANSLATE to
                                stringResource(MR.strings.pref_translation_engine_google),
                            TranslationPreferences.ENGINE_GEMINI to
                                stringResource(MR.strings.pref_translation_engine_gemini),
                            TranslationPreferences.ENGINE_OPENROUTER to
                                stringResource(MR.strings.pref_translation_engine_openrouter),
                        ),
                        title = stringResource(MR.strings.pref_translation_engine_type),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiApiKey,
                        title = stringResource(MR.strings.pref_translation_gemini_api_key),
                        enabled = engineType == TranslationPreferences.ENGINE_GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.geminiModel,
                        title = stringResource(MR.strings.pref_translation_gemini_model),
                        enabled = engineType == TranslationPreferences.ENGINE_GEMINI,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openRouterApiKey,
                        title = stringResource(MR.strings.pref_translation_openrouter_api_key),
                        enabled = engineType == TranslationPreferences.ENGINE_OPENROUTER,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.openRouterModel,
                        title = stringResource(MR.strings.pref_translation_openrouter_model),
                        enabled = engineType == TranslationPreferences.ENGINE_OPENROUTER,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_models_group),
                preferenceItems = listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_model_detection_title),
                        subtitle = if (detectionDownloaded) {
                            stringResource(MR.strings.label_downloaded)
                        } else {
                            stringResource(MR.strings.action_download)
                        },
                        onClick = {
                            scope.launch {
                                modelManager.downloadModel(
                                    ModelManager.MODEL_DETECTION,
                                    ModelManager.URL_DETECTION,
                                    ModelManager.FILE_DETECTION,
                                )
                            }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_model_ocr_title),
                        subtitle = if (ocrDownloaded) {
                            stringResource(MR.strings.label_downloaded)
                        } else {
                            stringResource(MR.strings.action_download)
                        },
                        onClick = {
                            scope.launch {
                                modelManager.downloadModel(
                                    ModelManager.MODEL_OCR,
                                    ModelManager.URL_OCR,
                                    ModelManager.FILE_OCR,
                                )
                            }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_model_inpainting_title),
                        subtitle = if (inpaintingDownloaded) {
                            stringResource(MR.strings.label_downloaded)
                        } else {
                            stringResource(MR.strings.action_download)
                        },
                        onClick = {
                            scope.launch {
                                modelManager.downloadModel(
                                    ModelManager.MODEL_INPAINTING,
                                    ModelManager.URL_INPAINTING,
                                    ModelManager.FILE_INPAINTING,
                                )
                            }
                        },
                    ),
                ),
            ),
        )
    }
}
