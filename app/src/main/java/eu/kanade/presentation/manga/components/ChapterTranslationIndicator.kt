package eu.kanade.presentation.manga.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.domain.translation.model.ChapterTranslationProgress
import eu.kanade.domain.translation.model.TranslationState
import eu.kanade.presentation.components.DropdownMenu
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    isDownloaded: Boolean,
    translationProgressProvider: () -> ChapterTranslationProgress,
    isTranslatedProvider: () -> Boolean,
    onStartTranslate: () -> Unit,
    onShowProgress: () -> Unit,
    onRetranslate: () -> Unit,
    onDeleteTranslation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isDownloaded) return

    val progress = translationProgressProvider()
    val isTranslated = isTranslatedProvider()

    when {
        progress.state == TranslationState.QUEUED || progress.state == TranslationState.RUNNING -> {
            TranslationProgressIndicator(
                enabled = enabled,
                modifier = modifier,
                onClick = onShowProgress,
            )
        }
        isTranslated || progress.state == TranslationState.TRANSLATED -> {
            TranslatedIndicator(
                enabled = enabled,
                modifier = modifier,
                onRetranslate = onRetranslate,
                onDelete = onDeleteTranslation,
            )
        }
        progress.state == TranslationState.ERROR -> {
            TranslationErrorIndicator(
                enabled = enabled,
                modifier = modifier,
                onClick = onShowProgress,
            )
        }
        else -> {
            NotTranslatedIndicator(
                enabled = enabled,
                modifier = modifier,
                onClick = onStartTranslate,
            )
        }
    }
}

@Composable
private fun NotTranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = onClick,
                onClick = onClick,
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Public,
            contentDescription = "Translate Chapter",
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslationProgressIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = onClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(IndicatorSize)
                .padding(2.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            trackColor = Color.Transparent,
        )
        Icon(
            imageVector = MaterialSymbols.Rounded.Public,
            contentDescription = "Translation in Progress",
            modifier = Modifier.size(IndicatorSize - 8.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onRetranslate: () -> Unit,
    onDelete: () -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.RoundedFilled.CheckCircle,
            contentDescription = "Translated",
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = "Terjemahkan Ulang") },
                onClick = {
                    onRetranslate()
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Hapus Terjemahan") },
                onClick = {
                    onDelete()
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun TranslationErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = onClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Error,
            contentDescription = "Translation Error",
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

private val IndicatorSize = 26.dp
