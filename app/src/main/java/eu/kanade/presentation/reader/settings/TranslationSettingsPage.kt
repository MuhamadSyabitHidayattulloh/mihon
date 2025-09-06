package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun ColumnScope.TranslationPage(screenModel: ReaderSettingsScreenModel) {
    val prefs = screenModel.translationPreferences
    val showTranslation by prefs.showTranslation().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_show_translation),
        checked = showTranslation,
        onCheckedChange = { prefs.showTranslation().set(it) },
    )

    val offsetX by prefs.offsetX().collectAsState()
    SliderItem(
        label = stringResource(MR.strings.pref_offset_x),
        value = offsetX,
        valueText = "$offsetX",
        onChange = { prefs.offsetX().set(it) },
        valueRange = -100f..100f,
    )

    val offsetY by prefs.offsetY().collectAsState()
    SliderItem(
        label = stringResource(MR.strings.pref_offset_y),
        value = offsetY,
        valueText = "$offsetY",
        onChange = { prefs.offsetY().set(it) },
        valueRange = -100f..100f,
    )

    val offsetZ by prefs.offsetZ().collectAsState()
    SliderItem(
        label = stringResource(MR.strings.pref_offset_z),
        value = offsetZ,
        valueText = "$offsetZ",
        onChange = { prefs.offsetZ().set(it) },
        valueRange = -100f..100f,
    )
}
