package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.domain.translation.model.OnnxModelType
import tachiyomi.domain.translation.model.TranslationEngine
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.io.File

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val translationPreferences = remember { context.appGraph.translationPreferences }

        val engine by translationPreferences.translationEngine.collectAsState()
        val detectorDownloaded by translationPreferences.detectorModelDownloaded.collectAsState()
        val ocrDownloaded by translationPreferences.ocrModelDownloaded.collectAsState()
        val inpaintingDownloaded by translationPreferences.inpaintingModelDownloaded.collectAsState()

        var geminiApiKey by remember { mutableStateOf(translationPreferences.geminiApiKey.get()) }
        var geminiModel by remember { mutableStateOf(translationPreferences.geminiModel.get()) }
        var openRouterApiKey by remember { mutableStateOf(translationPreferences.openRouterApiKey.get()) }
        var openRouterModel by remember { mutableStateOf(translationPreferences.openRouterModel.get()) }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.autoTranslateAfterDownload,
                title = stringResource(MR.strings.pref_auto_translate_after_download),
                subtitle = stringResource(MR.strings.pref_auto_translate_after_download_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.sourceLanguage,
                entries = TranslationLanguage.values().associateWith { it.displayName },
                title = stringResource(MR.strings.pref_translation_source_language),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.targetLanguage,
                entries = TranslationLanguage.values().associateWith { it.displayName },
                title = stringResource(MR.strings.pref_translation_target_language),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationEngine,
                entries = TranslationEngine.values().associateWith { it.displayName },
                title = stringResource(MR.strings.pref_translation_engine),
            ),
            if (engine == TranslationEngine.GEMINI) {
                Preference.PreferenceGroup(
                    title = "Gemini AI Settings",
                    preferenceItems = listOf(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.geminiApiKey,
                            title = "Gemini API Key",
                            subtitle = if (geminiApiKey.isBlank()) "Not set" else "••••••••",
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.geminiModel,
                            title = "Gemini Model",
                            subtitle = geminiModel,
                        ),
                    ),
                )
            } else {
                null
            },
            if (engine == TranslationEngine.OPENROUTER) {
                Preference.PreferenceGroup(
                    title = "OpenRouter Settings",
                    preferenceItems = listOf(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.openRouterApiKey,
                            title = "OpenRouter API Key",
                            subtitle = if (openRouterApiKey.isBlank()) "Not set" else "••••••••",
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = translationPreferences.openRouterModel,
                            title = "OpenRouter Model",
                            subtitle = openRouterModel,
                        ),
                    ),
                )
            } else {
                null
            },
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_models),
                preferenceItems = listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = "Text Bounding Detector Model",
                        subtitle = if (detectorDownloaded) {
                            stringResource(
                                MR.strings.model_downloaded,
                            )
                        } else {
                            stringResource(MR.strings.model_not_downloaded)
                        },
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                downloadModel(
                                    context,
                                    OnnxModelType.DETECTOR,
                                    "https://huggingface.co/ogkalu/comic-text-and-bubble-detector/resolve/main/detector-v4-s_int8.onnx",
                                )
                                translationPreferences.detectorModelDownloaded.set(true)
                            }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "PP-OCRv6 Recognition Model",
                        subtitle = if (ocrDownloaded) {
                            stringResource(
                                MR.strings.model_downloaded,
                            )
                        } else {
                            stringResource(MR.strings.model_not_downloaded)
                        },
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                downloadModel(
                                    context,
                                    OnnxModelType.OCR_REC,
                                    "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.onnx",
                                )
                                downloadModel(
                                    context,
                                    OnnxModelType.OCR_KEYS,
                                    "https://huggingface.co/ogkalu/ppocr-v6-onnx/resolve/main/PP-OCRv6_small_rec.txt",
                                )
                                translationPreferences.ocrModelDownloaded.set(true)
                            }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "AOT Inpainting Model",
                        subtitle = if (inpaintingDownloaded) {
                            stringResource(
                                MR.strings.model_downloaded,
                            )
                        } else {
                            stringResource(MR.strings.model_not_downloaded)
                        },
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                downloadModel(
                                    context,
                                    OnnxModelType.INPAINTING,
                                    "https://huggingface.co/ogkalu/aot-inpainting/resolve/main/aot.onnx",
                                )
                                translationPreferences.inpaintingModelDownloaded.set(true)
                            }
                        },
                    ),
                ),
            ),
        ).filterNotNull()
    }

    private fun downloadModel(context: android.content.Context, type: OnnxModelType, urlStr: String) {
        try {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, type.filename)

            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connect()
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
