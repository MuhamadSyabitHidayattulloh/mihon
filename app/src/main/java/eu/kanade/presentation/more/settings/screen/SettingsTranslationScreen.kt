package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.AlertDialog
import tachiyomi.presentation.core.i18n.stringResource

object SettingsTranslationScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var showDialog by remember { mutableStateOf(false) }

        if (showDialog) {
            var languageName by remember { mutableStateOf("") }
            var languageCode by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = stringResource(MR.strings.pref_add_language)) },
                text = {
                    Column {
                        TextField(
                            value = languageName,
                            onValueChange = { languageName = it },
                            label = { Text(text = stringResource(MR.strings.language_name)) },
                        )
                        TextField(
                            value = languageCode,
                            onValueChange = { languageCode = it },
                            label = { Text(text = stringResource(MR.strings.language_code)) },
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showDialog = false }) {
                        Text(text = stringResource(MR.strings.action_add))
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        PreferenceScaffold(
            title = stringResource(MR.strings.label_translation),
            onBackPressed = navigator::pop,
        ) {
            Preference.Group(
                title = stringResource(MR.strings.pref_translation_settings),
                icon = Icons.Outlined.Translate,
            ) {
                Preference.Switch(
                    pref = remember { mutableStateOf(false) },
                    title = stringResource(MR.strings.pref_auto_translate_on_download),
                )
                Preference.ListMenu(
                    pref = remember { mutableStateOf(0) },
                    title = stringResource(MR.strings.pref_translate_from),
                    items = listOf("Latin", "Chinese Simplified", "Chinese Traditional", "Japanese", "Korean"),
                )
                Preference.ListMenu(
                    pref = remember { mutableStateOf(0) },
                    title = stringResource(MR.strings.pref_translate_to),
                    items = listOf("English", "Indonesian"),
                )
                Preference.Text(
                    title = stringResource(MR.strings.pref_add_language),
                    onClick = { showDialog = true },
                )
                Preference.ListMenu(
                    pref = remember { mutableStateOf(0) },
                    title = stringResource(MR.strings.pref_translation_model),
                    items = listOf("Google Translate", "ML Kit (offline)", "Bing Translate"),
                )
            }
            Preference.Group(
                title = stringResource(MR.strings.pref_appearance_settings),
            ) {
                Preference.Text(
                    title = stringResource(MR.strings.pref_background_color),
                    widget = {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(32.dp)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.Red),
                        )
                    },
                    onClick = { /*TODO: open color picker*/ },
                )
                Preference.ListMenu(
                    pref = remember { mutableStateOf(0) },
                    title = stringResource(MR.strings.pref_font_family),
                    items = listOf("Sans", "Serif", "Monospace"),
                )
                Preference.SeekBar(
                    pref = remember { mutableStateOf(16) },
                    title = stringResource(MR.strings.pref_font_size),
                    min = 10,
                    max = 32,
                )
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(Color.Gray)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Translated text preview.",
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
