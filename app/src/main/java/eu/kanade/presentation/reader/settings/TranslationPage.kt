package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.MR

@Composable
fun TranslationPage() {
    Column {
        Preference.Switch(
            pref = remember { androidx.compose.runtime.mutableStateOf(false) },
            title = stringResource(MR.strings.pref_show_translation),
        )
        Preference.SeekBar(
            pref = remember { androidx.compose.runtime.mutableStateOf(0) },
            title = stringResource(MR.strings.pref_offset_x),
            min = -100,
            max = 100,
        )
        Preference.SeekBar(
            pref = remember { androidx.compose.runtime.mutableStateOf(0) },
            title = stringResource(MR.strings.pref_offset_y),
            min = -100,
            max = 100,
        )
        Preference.SeekBar(
            pref = remember { androidx.compose.runtime.mutableStateOf(0) },
            title = stringResource(MR.strings.pref_offset_z),
            min = -100,
            max = 100,
        )
    }
}
