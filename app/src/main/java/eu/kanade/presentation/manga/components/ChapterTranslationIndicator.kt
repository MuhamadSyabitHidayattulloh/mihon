package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.translation.TranslationProgress
import eu.kanade.tachiyomi.data.translation.TranslationStatus
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Code
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterTranslationAction {
    START,
    START_NOW,
    CANCEL,
    DELETE,
}

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    translationProgressProvider: () -> TranslationProgress,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = translationProgressProvider()
    var showProgressDialog by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        TranslationProgressDialog(
            progress = progress,
            onDismiss = { showProgressDialog = false },
        )
    }

    when (progress.status) {
        TranslationStatus.NOT_TRANSLATED -> NotTranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        TranslationStatus.QUEUED,
        TranslationStatus.DETECTING,
        TranslationStatus.OCR,
        TranslationStatus.CLEANING,
        TranslationStatus.TRANSLATING,
        TranslationStatus.CANVAS_RENDERING,
        -> TranslatingIndicator(
            enabled = enabled,
            modifier = modifier,
            progress = progress,
            onClickProgress = { showProgressDialog = true },
            onClickAction = onClick,
        )
        TranslationStatus.TRANSLATED -> TranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        TranslationStatus.ERROR -> ErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotTranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.START_NOW) },
                onClick = { onClick(ChapterTranslationAction.START) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Code,
            contentDescription = "Terjemahkan",
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslatingIndicator(
    enabled: Boolean,
    progress: TranslationProgress,
    onClickProgress: () -> Unit,
    onClickAction: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClickAction(ChapterTranslationAction.CANCEL) },
                onClick = { onClickProgress() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
        val animatedProgress by animateFloatAsState(
            targetValue = progress.progress / 100f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "translation_progress",
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = IndicatorModifier,
            color = strokeColor,
            strokeWidth = IndicatorSize / 2,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
        Icon(
            imageVector = MaterialSymbols.Rounded.Code,
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize - 8.dp),
            tint = strokeColor,
        )
    }
}

@Composable
private fun TranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
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
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = "Terjemahkan Ulang") },
                onClick = {
                    onClick(ChapterTranslationAction.START)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Hapus Terjemahan") },
                onClick = {
                    onClick(ChapterTranslationAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterTranslationAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterTranslationAction.START) },
                onClick = { onClick(ChapterTranslationAction.START) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Error,
            contentDescription = "Gagal menerjemahkan",
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun TranslationProgressDialog(
    progress: TranslationProgress,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Progres Terjemahan") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tahap: ${progress.stageName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Queue Selesai: ${progress.doneQueue} | Gagal: ${progress.failedQueue}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Log Kesalahan & Progres:",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(progress.logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Tutup")
            }
        },
    )
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
private val IndicatorPadding = 2.dp
private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
