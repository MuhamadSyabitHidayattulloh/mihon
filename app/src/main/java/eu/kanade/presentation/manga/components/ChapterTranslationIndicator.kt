package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (enabled) {
        Box(modifier = modifier) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = stringResource(MR.strings.label_translation),
                )
            }
        }
    }
}
