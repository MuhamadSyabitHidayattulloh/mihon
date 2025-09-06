package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.asString
import eu.kanade.tachiyomi.ui.reader.setting.TranslationPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPref = remember { Injekt.get<TranslationPreferences>() }

        val fromPref = translationPref.translateFrom()
        val toPref = translationPref.translateTo()
        val modelPref = translationPref.translationModel()

        val fontFamilyPref = translationPref.fontFamily()
        val fontSizePref = translationPref.fontSize()
        val fontColorPref = translationPref.fontColor()
        val backgroundColorPref = translationPref.backgroundColor()

        val fontFamily by fontFamilyPref.collectAsState()
        val fontSize by fontSizePref.collectAsState()
        val fontColor by fontColorPref.collectAsState()
        val backgroundColor by backgroundColorPref.collectAsState()

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPref.autoTranslateOnDownload(),
                title = stringResource(MR.strings.pref_auto_translate_on_download),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_configuration),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = fromPref,
                        title = stringResource(MR.strings.pref_translate_from),
                        entries = persistentMapOf(
                            "latin" to "Latin",
                            "zh-CN" to "Chinese (Simplified)",
                            "zh-TW" to "Chinese (Traditional)",
                            "ja" to "Japanese",
                            "ko" to "Korean",
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = toPref,
                        title = stringResource(MR.strings.pref_translate_to),
                        entries = persistentMapOf(
                            "en" to "English",
                            "id" to "Indonesian",
                        ),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = modelPref,
                        title = stringResource(MR.strings.pref_translation_model),
                        entries = persistentMapOf(
                            "mlkit" to "ML Kit",
                            "google" to "Google Translate",
                            "bing" to "Bing Translate",
                        ),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_appearance),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = fontFamilyPref,
                        title = "Font family",
                        entries = persistentMapOf(
                            "Default" to "Default",
                        ),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = fontSize.toInt(),
                        valueRange = 10..32,
                        title = "Font size",
                        subtitle = "${fontSize.toInt()}sp",
                        onValueChanged = {
                            fontSizePref.set(it.toFloat())
                            true
                        },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = fontColorPref.asString(),
                        title = "Font color",
                        subtitle = "#${fontColorPref.asString().get()}",
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = backgroundColorPref.asString(),
                        title = "Background color",
                        subtitle = "#${backgroundColorPref.asString().get()}",
                    ),
                    Preference.PreferenceItem.CustomPreference(
                        title = "Preview",
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(backgroundColor.toULong()))
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                                style = TextStyle(
                                    color = Color(fontColor.toULong()),
                                    fontSize = fontSize.sp,
                                    fontFamily = when (fontFamily) {
                                        "Default" -> FontFamily.Default
                                        else -> FontFamily.Default
                                    },
                                ),
                            )
                        }
                    },
                ),
            ),
        )
    }
}
