package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Translate
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import tachiyomi.domain.translation.model.TranslationProgress
import tachiyomi.domain.translation.model.TranslationState
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
    progress: TranslationProgress,
    isDownloaded: Boolean,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isDownloaded) return

    var showProgressDialog by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        TranslationProgressDialog(
            progress = progress,
            onDismissRequest = { showProgressDialog = false },
        )
    }

    when (progress.state) {
        TranslationState.NOT_TRANSLATED -> {
            Box(
                modifier = modifier
                    .size(IconButtonTokens.StateLayerSize)
                    .commonClickable(
                        enabled = enabled,
                        hapticFeedback = LocalHapticFeedback.current,
                        onLongClick = { onClick(ChapterTranslationAction.START) },
                        onClick = { onClick(ChapterTranslationAction.START) },
                    )
                    .secondaryItemAlpha(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Translate,
                    contentDescription = "Translate Chapter",
                    modifier = Modifier.size(IndicatorSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TranslationState.QUEUED, TranslationState.TRANSLATING -> {
            Box(
                modifier = modifier
                    .size(IconButtonTokens.StateLayerSize)
                    .commonClickable(
                        enabled = enabled,
                        hapticFeedback = LocalHapticFeedback.current,
                        onLongClick = { showProgressDialog = true },
                        onClick = { showProgressDialog = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val strokeColor = MaterialTheme.colorScheme.primary
                if (progress.totalPages == 0) {
                    CircularProgressIndicator(
                        modifier = IndicatorModifier,
                        color = strokeColor,
                        strokeWidth = IndicatorStrokeWidth,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Butt,
                    )
                } else {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.progressPages.toFloat() / progress.totalPages.coerceAtLeast(1),
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "progress",
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
                }
                Icon(
                    imageVector = MaterialSymbols.Rounded.Translate,
                    contentDescription = null,
                    modifier = ArrowModifier,
                    tint = strokeColor,
                )
            }
        }
        TranslationState.TRANSLATED -> {
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
                        text = { Text(text = "Re-translate") },
                        onClick = {
                            onClick(ChapterTranslationAction.RETRANSLATE)
                            isMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(MR.strings.action_delete)) },
                        onClick = {
                            onClick(ChapterTranslationAction.DELETE)
                            isMenuExpanded = false
                        },
                    )
                }
            }
        }
        TranslationState.ERROR -> {
            Box(
                modifier = modifier
                    .size(IconButtonTokens.StateLayerSize)
                    .commonClickable(
                        enabled = enabled,
                        hapticFeedback = LocalHapticFeedback.current,
                        onLongClick = { showProgressDialog = true },
                        onClick = { showProgressDialog = true },
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
    }
}

@Composable
fun TranslationProgressDialog(
    progress: TranslationProgress,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Translation Progress") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Text(text = "Status: ${progress.state.name}")
                Text(text = "Stage: ${progress.stage.name}")
                Spacer(modifier = Modifier.height(8.dp))

                if (progress.totalPages > 0) {
                    val floatProgress = progress.progressPages.toFloat() / progress.totalPages
                    LinearProgressIndicator(
                        progress = { floatProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Page ${progress.progressPages} of ${progress.totalPages}")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Logs:", style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = progress.logs.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Close")
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
private val IndicatorStrokeWidth = IndicatorPadding

private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
private val ArrowModifier = Modifier
    .size(IndicatorSize - 7.dp)
