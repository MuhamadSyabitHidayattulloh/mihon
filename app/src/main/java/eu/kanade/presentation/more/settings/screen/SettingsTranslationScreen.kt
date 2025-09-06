package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.translation.service.TranslationPreferences
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.Screen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.presentation.more.settings.PreferenceScaffold

object SettingsTranslationScreen : Screen() {

    @Composable
    override fun Content() {
        val prefs = remember { Injekt.get<TranslationPreferences>() }

        val colors = listOf(
            Color.Black,
            Color.DarkGray,
            Color.Gray,
            Color.LightGray,
            Color.White,
            Color.Red,
            Color.Green,
            Color.Blue,
        )

        PreferenceScaffold(
            title = stringResource(MR.strings.pref_category_translation),
            items = {
                item {
                    val autoTranslate by prefs.autoTranslateAfterDownload().collectAsState()
                    SwitchPreferenceWidget(
                        title = stringResource(MR.strings.pref_auto_translate_after_download),
                        checked = autoTranslate,
                        onCheckedChanged = { prefs.autoTranslateAfterDownload().set(it) },
                    )
                }

                item {
                    val translateFrom by prefs.translateFrom().collectAsState()
                    ListPreferenceWidget(
                        title = stringResource(MR.strings.pref_translate_from),
                        value = translateFrom,
                        entries = persistentMapOf(
                            "la" to stringResource(MR.strings.language_latin),
                            "zh-CN" to stringResource(MR.strings.language_chinese_simplified),
                            "zh-TW" to stringResource(MR.strings.language_chinese_traditional),
                            "ja" to stringResource(MR.strings.language_japanese),
                            "ko" to stringResource(MR.strings.language_korean),
                        ),
                        onValueChanged = { prefs.translateFrom().set(it); true },
                    )
                }

                item {
                    val translateTo by prefs.translateTo().collectAsState()
                    ListPreferenceWidget(
                        title = stringResource(MR.strings.pref_translate_to),
                        value = translateTo,
                        entries = persistentMapOf(
                            "en" to stringResource(MR.strings.language_english),
                            "id" to stringResource(MR.strings.language_indonesian),
                        ),
                        onValueChanged = { prefs.translateTo().set(it); true },
                    )
                }

                var showDialog by remember { mutableStateOf(false) }
                if (showDialog) {
                    AddLanguageDialog(onDismissRequest = { showDialog = false })
                }
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.action_add_language),
                        onClick = { showDialog = true },
                    )
                }

                item {
                    val translationModel by prefs.translationModel().collectAsState()
                    ListPreferenceWidget(
                        title = stringResource(MR.strings.pref_translation_model),
                        value = translationModel,
                        entries = persistentMapOf(
                            "google" to stringResource(MR.strings.model_google_translate),
                            "mlkit" to stringResource(MR.strings.model_ml_kit),
                            "bing" to stringResource(MR.strings.model_bing_translate),
                        ),
                        onValueChanged = { prefs.translationModel().set(it); true },
                    )
                }

                item {
                    PreferenceGroupHeader(title = stringResource(MR.strings.pref_category_appearance_settings))
                }

                val backgroundColor by prefs.backgroundColor().collectAsState()
                val selectedColor = remember(backgroundColor) { Color(backgroundColor.toULong()) }
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.pref_background_color),
                        widget = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .height(24.dp)
                                    .fillMaxWidth()
                                    .background(
                                        color = selectedColor,
                                        shape = MaterialTheme.shapes.small,
                                    ),
                            )
                        },
                        onClick = {
                            val currentIndex = colors.indexOfFirst { it.value.toLong() == backgroundColor }
                            val nextIndex = (currentIndex + 1) % colors.size
                            prefs.backgroundColor().set(colors[nextIndex].value.toLong())
                        },
                    )
                }

                val fontFamily by prefs.fontFamily().collectAsState()
                item {
                    ListPreferenceWidget(
                        title = stringResource(MR.strings.pref_font_family),
                        value = fontFamily,
                        entries = persistentMapOf(
                            "sans" to stringResource(MR.strings.font_sans),
                            "serif" to stringResource(MR.strings.font_serif),
                            "monospace" to stringResource(MR.strings.font_monospace),
                        ),
                        onValueChanged = { prefs.fontFamily().set(it); true },
                    )
                }

                val fontSize by prefs.fontSize().collectAsState()
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.pref_font_size),
                        subtitle = "${fontSize}sp",
                        widget = {
                            Slider(
                                value = fontSize.toFloat(),
                                onValueChange = { prefs.fontSize().set(it.toInt()) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                valueRange = 10f..32f,
                                steps = 21,
                            )
                        },
                    )
                }

                item {
                    PreviewBox(
                        backgroundColor = selectedColor,
                        fontFamily = when (fontFamily) {
                            "serif" -> FontFamily.Serif
                            "monospace" -> FontFamily.Monospace
                            else -> FontFamily.SansSerif
                        },
                        fontSize = fontSize.toFloat(),
                    )
                }
            },
        )
    }

    @Composable
    private fun AddLanguageDialog(onDismissRequest: () -> Unit) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(MR.strings.action_add_language)) },
            text = {
                Column {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(MR.strings.language_name)) },
                    )
                    TextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(MR.strings.language_code)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun PreviewBox(
        backgroundColor: Color,
        fontFamily: FontFamily,
        fontSize: Float,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(MR.strings.pref_preview_box),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "The quick brown fox jumps over the lazy dog",
                    fontFamily = fontFamily,
                    fontSize = fontSize.sp,
                    color = if (backgroundColor.isDark()) Color.White else Color.Black,
                )
            }
        }
    }

    private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5
}
