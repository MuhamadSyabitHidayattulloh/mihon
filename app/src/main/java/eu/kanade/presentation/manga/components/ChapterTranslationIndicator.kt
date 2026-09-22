package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Translate
import tachiyomi.domain.translation.model.TranslationProgress
import tachiyomi.domain.translation.model.TranslationState
import mihon.icons.materialsymbols.roundedfilled.Translate as FilledTranslate

@Composable
fun ChapterTranslationIndicator(
    translationProgress: TranslationProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (translationProgress.state) {
            TranslationState.NOT_TRANSLATED -> {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Translate,
                    contentDescription = "Translate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
            }
            TranslationState.QUEUED, TranslationState.TRANSLATING -> {
                val progress = if (translationProgress.totalPages > 0) {
                    translationProgress.progressPagesDone.toFloat() / translationProgress.totalPages
                } else {
                    0f
                }
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Icon(
                    imageVector = MaterialSymbols.Rounded.Translate,
                    contentDescription = "Translating",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            TranslationState.TRANSLATED -> {
                Icon(
                    imageVector = MaterialSymbols.FilledTranslate,
                    contentDescription = "Translated",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            TranslationState.ERROR -> {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Error,
                    contentDescription = "Translation Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
