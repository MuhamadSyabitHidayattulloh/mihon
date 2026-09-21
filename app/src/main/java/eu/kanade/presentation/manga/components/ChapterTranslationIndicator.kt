package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import eu.kanade.tachiyomi.data.translation.model.ChapterTranslation
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Translate
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterTranslationAction {
    START,
    CANCEL,
    DELETE,
    RETRANSLATE,
}

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    translationStateProvider: () -> ChapterTranslation.State,
    translationProgressProvider: () -> Float,
    translationStageProvider: () -> String,
    translationLogsProvider: () -> List<String>,
    translationPagesProvider: () -> Pair<Int, Int>,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProgressDialog by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        TranslationProgressDialog(
            stage = translationStageProvider(),
            progress = translationProgressProvider(),
            logs = translationLogsProvider(),
            pages = translationPagesProvider(),
            state = translationStateProvider(),
            onDismiss = { showProgressDialog = false },
            onCancel = {
                onClick(ChapterTranslationAction.CANCEL)
                showProgressDialog = false
            },
        )
    }

    when (val state = translationStateProvider()) {
        ChapterTranslation.State.NOT_TRANSLATED -> NotTranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = { onClick(ChapterTranslationAction.START) },
        )
        ChapterTranslation.State.QUEUE, ChapterTranslation.State.TRANSLATING -> TranslatingIndicator(
            enabled = enabled,
            modifier = modifier,
            state = state,
            progressProvider = translationProgressProvider,
            onClick = { showProgressDialog = true },
        )
        ChapterTranslation.State.TRANSLATED -> TranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        ChapterTranslation.State.ERROR -> ErrorTranslationIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = { showProgressDialog = true },
        )
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
            imageVector = MaterialSymbols.Rounded.Translate,
            contentDescription = stringResource(MR.strings.action_translate),
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslatingIndicator(
    enabled: Boolean,
    state: ChapterTranslation.State,
    progressProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        val strokeColor = MaterialTheme.colorScheme.primary
        val progress = progressProvider()
        if (state == ChapterTranslation.State.QUEUE || progress == 0f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .padding(2.dp),
                color = strokeColor,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        } else {
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "trans_progress",
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .size(22.dp)
                    .padding(2.dp),
                color = strokeColor,
                strokeWidth = 3.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        }
        Icon(
            imageVector = MaterialSymbols.Rounded.Translate,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
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
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_retranslate)) },
                onClick = {
                    onClick(ChapterTranslationAction.RETRANSLATE)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete_translation)) },
                onClick = {
                    onClick(ChapterTranslationAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorTranslationIndicator(
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
            contentDescription = stringResource(MR.strings.translation_status_failed),
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TranslationProgressDialog(
    stage: String,
    progress: Float,
    logs: List<String>,
    pages: Pair<Int, Int>,
    state: ChapterTranslation.State,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(MR.strings.translation_progress)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (stage.isNotBlank()) stage else state.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pages: ${pages.first}/${pages.second}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (logs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(MR.strings.translation_logs),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    logs.forEach { log ->
                        Text(
                            text = "• $log",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            if (state == ChapterTranslation.State.QUEUE || state == ChapterTranslation.State.TRANSLATING) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
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
