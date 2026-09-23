package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.TranslationModelManager
import kotlinx.coroutines.launch
import tachiyomi.domain.translation.service.TranslationPreferences

@Inject
@SingleIn(AppScope::class)
class SettingsTranslationScreen(
    private val translationPreferences: TranslationPreferences,
    private val modelManager: TranslationModelManager,
) : SearchableSettings {

    @Composable
    override fun getTitleRes(): Int = 0

    @Composable
    fun getTitleString(): String = "Terjemahan"

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val downloadStates by modelManager.downloadStates.collectAsState()

        val enginePref = translationPreferences.translatorEngine()
        val currentEngine by enginePref.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = "Pengaturan Terjemahan",
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = translationPreferences.autoTranslateAfterDownload,
                        title = "Unduh terjemahan setelah mendownload chapter",
                        subtitle = "Otomatis menerjemahkan chapter setelah proses download selesai",
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = translationPreferences.translateFrom,
                        title = "Translate from",
                        subtitle = "%s",
                        entries = mapOf(
                            "en" to "English",
                            "zh" to "China",
                            "ja" to "Jepang",
                            "ko" to "Korea",
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = translationPreferences.translateTo,
                        title = "Translate to",
                        subtitle = "%s",
                        entries = mapOf(
                            "id" to "Indonesian",
                            "en" to "English",
                            "es" to "Spanish",
                            "fr" to "French",
                            "de" to "German",
                            "ja" to "Japanese",
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = translationPreferences.translatorEngine,
                        title = "Tipe Translator",
                        subtitle = "%s",
                        entries = mapOf(
                            TranslationPreferences.ENGINE_MLKIT to "ML-Kit Translate (Offline)",
                            TranslationPreferences.ENGINE_GOOGLE_WEB to "Google Translate (Web Based)",
                            TranslationPreferences.ENGINE_GEMINI to "Gemini AI",
                            TranslationPreferences.ENGINE_OPENROUTER to "OpenRouter",
                        ),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Konfigurasi AI / API",
                preferenceItems = buildList {
                    if (currentEngine == TranslationPreferences.ENGINE_GEMINI) {
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                pref = translationPreferences.geminiApiKey,
                                title = "Gemini API Key",
                                subtitle = "Masukkan Gemini API Key",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                pref = translationPreferences.geminiModel,
                                title = "Gemini Model",
                                subtitle = "Default: gemini-2.5-flash",
                            ),
                        )
                    }
                    if (currentEngine == TranslationPreferences.ENGINE_OPENROUTER) {
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                pref = translationPreferences.openRouterApiKey,
                                title = "OpenRouter API Key",
                                subtitle = "Masukkan OpenRouter API Key",
                            ),
                        )
                        add(
                            Preference.PreferenceItem.EditTextPreference(
                                pref = translationPreferences.openRouterModel,
                                title = "OpenRouter Model",
                                subtitle = "Default: google/gemini-flash-1.5",
                            ),
                        )
                    }
                },
            ),
            Preference.PreferenceGroup(
                title = "Model AI / ONNX (Offline)",
                preferenceItems = TranslationModelManager.ModelType.entries.map { modelType ->
                    val state = downloadStates[modelType] ?: TranslationModelManager.DownloadState.NotDownloaded
                    Preference.PreferenceItem.CustomPreference(
                        title = modelType.displayName,
                        content = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = modelType.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        val statusText = when (state) {
                                            is TranslationModelManager.DownloadState.Downloaded -> "Selesai diunduh"
                                            is TranslationModelManager.DownloadState.Downloading -> "Mengunduh ${(state.progress * 100).toInt()}%"
                                            is TranslationModelManager.DownloadState.NotDownloaded -> "Belum diunduh"
                                            is TranslationModelManager.DownloadState.Error -> "Gagal: ${state.message}"
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    when (state) {
                                        is TranslationModelManager.DownloadState.Downloaded -> {
                                            OutlinedButton(
                                                onClick = { modelManager.deleteModel(modelType) },
                                            ) {
                                                Text("Hapus")
                                            }
                                        }
                                        is TranslationModelManager.DownloadState.Downloading -> {
                                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                        }
                                        else -> {
                                            Button(
                                                onClick = {
                                                    scope.launch { modelManager.downloadModel(modelType) }
                                                },
                                            ) {
                                                Text("Unduh")
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                },
            ),
        )
    }
}
