package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.translation.onnx.ModelDownloadStatus
import eu.kanade.domain.translation.service.TranslationPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.util.LocalBackPress
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): StringResource = MR.strings.pref_category_downloads

    @Composable
    override fun Content() {
        val handleBack = LocalBackPress.current
        PreferenceScaffold(
            titleRes = getTitleRes(),
            onBackPressed = if (handleBack != null) handleBack::invoke else null,
            itemsProvider = { getPreferences() },
        )
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val translationPreferences = remember { context.appGraph.translationPreferences }
        val modelDownloader = remember { context.appGraph.translationModelDownloader }

        val translatorType by translationPreferences.translatorType.collectAsState()
        val modelsStatus by modelDownloader.status.collectAsState()

        val mainGroup = Preference.PreferenceGroup(
            title = "General Translation Settings",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.autoTranslateAfterDownload,
                    title = "Auto-translate after download",
                    subtitle = "Automatically translate newly downloaded chapters",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translateFrom,
                    entries = mapOf(
                        "en" to "English",
                        "zh" to "China",
                        "ja" to "Jepang",
                        "ko" to "Korea",
                    ),
                    title = "Translate from",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translateTo,
                    entries = mapOf(
                        "id" to "Indonesian",
                        "en" to "English",
                        "es" to "Spanish",
                        "fr" to "French",
                        "de" to "German",
                        "ja" to "Japanese",
                        "ko" to "Korean",
                        "zh" to "Chinese",
                        "ru" to "Russian",
                        "pt" to "Portuguese",
                        "vi" to "Vietnamese",
                        "th" to "Thai",
                        "ar" to "Arabic",
                        "it" to "Italian",
                    ),
                    title = "Translate to",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translatorType,
                    entries = mapOf(
                        TranslationPreferences.TRANSLATOR_MLKIT to "ML-Kit Translate (Offline)",
                        TranslationPreferences.TRANSLATOR_GOOGLE to "Google Translate (Web Based)",
                        TranslationPreferences.TRANSLATOR_GEMINI to "Gemini AI",
                        TranslationPreferences.TRANSLATOR_OPENROUTER to "OpenRouter",
                    ),
                    title = "Translator Engine",
                ),
            ),
        )

        val apiGroupItems = mutableListOf<Preference.PreferenceItem<out Any, out Any>>()
        if (translatorType == TranslationPreferences.TRANSLATOR_GEMINI) {
            val geminiKeySet = translationPreferences.geminiApiKey.get().isNotBlank()
            apiGroupItems.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.geminiApiKey,
                    title = "Gemini API Key",
                    subtitle = if (geminiKeySet) "••••••••" else "Not Set",
                ),
            )
            apiGroupItems.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.geminiModel,
                    title = "Gemini Model",
                    subtitle = translationPreferences.geminiModel.get().ifBlank { "gemini-2.5-flash" },
                ),
            )
        } else if (translatorType == TranslationPreferences.TRANSLATOR_OPENROUTER) {
            val openRouterKeySet = translationPreferences.openRouterApiKey.get().isNotBlank()
            apiGroupItems.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.openRouterApiKey,
                    title = "OpenRouter API Key",
                    subtitle = if (openRouterKeySet) "••••••••" else "Not Set",
                ),
            )
            apiGroupItems.add(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.openRouterModel,
                    title = "OpenRouter Model",
                    subtitle = translationPreferences.openRouterModel.get().ifBlank { "google/gemini-2.5-flash" },
                ),
            )
        }

        val apiGroup = if (apiGroupItems.isNotEmpty()) {
            Preference.PreferenceGroup(
                title = "AI Engine Configuration",
                preferenceItems = apiGroupItems,
            )
        } else {
            null
        }

        val formatStatus = { status: ModelDownloadStatus ->
            when (status) {
                ModelDownloadStatus.DOWNLOADED -> "Downloaded / Ready"
                ModelDownloadStatus.DOWNLOADING -> "Downloading..."
                ModelDownloadStatus.NOT_DOWNLOADED -> "Not Downloaded"
                ModelDownloadStatus.ERROR -> "Download Failed"
            }
        }

        val modelsGroup = Preference.PreferenceGroup(
            title = "ONNX Models Status",
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Comic Text & Bubble Detector",
                    subtitle = formatStatus(modelsStatus.detectorStatus),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "PP-OCRv6 Recognition",
                    subtitle = formatStatus(modelsStatus.ocrStatus),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "AOT Inpainting Cleaner",
                    subtitle = formatStatus(modelsStatus.inpaintingStatus),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Download / Update Required Models",
                    subtitle = if (modelDownloader.isAllModelsDownloaded()) {
                        "All models available"
                    } else {
                        "Click to download required ONNX models"
                    },
                    onClick = {
                        coroutineScope.launch {
                            try {
                                modelDownloader.downloadAllModels()
                            } catch (_: Exception) {}
                        }
                    },
                ),
            ),
        )

        return listOfNotNull(mainGroup, apiGroup, modelsGroup)
    }
}
