package eu.kanade.presentation.manga.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CheckCircle
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Sync
import tachiyomi.domain.translation.model.TranslationStatus
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    statusProvider: () -> TranslationStatus,
    progressProvider: () -> Float,
    onClick: (TranslationStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    val status = statusProvider()
    val progress = progressProvider()

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            TranslationStatus.NOT_TRANSLATED -> {
                IconButton(onClick = { onClick(status) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Sync,
                        contentDescription = stringResource(MR.strings.action_translate),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            TranslationStatus.QUEUED, TranslationStatus.TRANSLATING -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .combinedClickable(
                            onClick = { onClick(status) },
                            role = Role.Button,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = false, radius = 20.dp),
                        ),
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
            TranslationStatus.TRANSLATED -> {
                IconButton(onClick = { onClick(status) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.CheckCircle,
                        contentDescription = stringResource(MR.strings.action_delete_translation),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TranslationStatus.ERROR -> {
                IconButton(onClick = { onClick(status) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Error,
                        contentDescription = stringResource(MR.strings.action_translate),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
