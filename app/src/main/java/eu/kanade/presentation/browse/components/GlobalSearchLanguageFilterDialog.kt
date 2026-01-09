package eu.kanade.presentation.browse.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchLanguageFilterDialog(
    availableLanguages: List<String>,
    selectedLanguages: Set<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val selected = remember {
        availableLanguages
            .filter { it in selectedLanguages }
            .toMutableStateList()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.action_display_language_badge)) },
        text = {
            LazyColumn {
                availableLanguages.forEach { language ->
                    item {
                        val isSelected = selected.contains(language)
                        LabeledCheckbox(
                            label = LocaleHelper.getSourceDisplayName(language, context),
                            checked = isSelected,
                            onCheckedChange = {
                                if (it) {
                                    selected.add(language)
                                } else {
                                    selected.remove(language)
                                }
                            },
                        )
                    }
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selected.toSet())
                    onDismissRequest()
                },
            ) {
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
