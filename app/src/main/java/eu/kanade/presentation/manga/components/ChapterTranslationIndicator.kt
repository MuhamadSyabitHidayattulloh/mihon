package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.translation.TranslationProgressState
import eu.kanade.tachiyomi.data.translation.TranslationStatus
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
    RETRANSLATE,
    DELETE,
}

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    downloadState: Download.State,
    translationState: TranslationProgressState,
    hasTranslation: Boolean,
    onTranslationAction: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only visible if chapter is already downloaded
    if (downloadState != Download.State.DOWNLOADED) return

    var showProgressDialog by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        TranslationProgressDialog(
            state = translationState,
            onDismissRequest = { showProgressDialog = false },
        )
    }

    when (translationState.status) {
        TranslationStatus.QUEUE, TranslationStatus.TRANSLATING -> TranslatingIndicator(
            enabled = enabled,
            state = translationState,
            onClick = { showProgressDialog = true },
            modifier = modifier,
        )
        TranslationStatus.TRANSLATED -> TranslatedIndicator(
            enabled = enabled,
            onTranslationAction = onTranslationAction,
            modifier = modifier,
        )
        TranslationStatus.ERROR -> ErrorIndicator(
            enabled = enabled,
            onClick = { showProgressDialog = true },
            modifier = modifier,
        )
        TranslationStatus.NOT_TRANSLATED -> {
            if (hasTranslation) {
                TranslatedIndicator(
                    enabled = enabled,
                    onTranslationAction = onTranslationAction,
                    modifier = modifier,
                )
            } else {
                NotTranslatedIndicator(
                    enabled = enabled,
                    onClick = { onTranslationAction(ChapterTranslationAction.START) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun NotTranslatedIndicator(
    enabled: Boolean,
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
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Translate,
            contentDescription = stringResource(MR.strings.pref_category_translation),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslatingIndicator(
    enabled: Boolean,
    state: TranslationProgressState,
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
        val total = state.totalCount.coerceAtLeast(1)
        val progress = (state.doneCount + state.failedCount) / total.toFloat()
        if (state.status == TranslationStatus.QUEUE || progress == 0f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp),
                color = MaterialTheme.colorScheme.primary,
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
                    .size(24.dp)
                    .padding(2.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        }
    }
}

@Composable
private fun TranslatedIndicator(
    enabled: Boolean,
    onTranslationAction: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_retranslate)) },
                onClick = {
                    onTranslationAction(ChapterTranslationAction.RETRANSLATE)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete_translation)) },
                onClick = {
                    onTranslationAction(ChapterTranslationAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
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
        Icon(
            imageVector = MaterialSymbols.Rounded.Error,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
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
