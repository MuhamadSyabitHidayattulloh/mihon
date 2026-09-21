package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Translate
import mihon.icons.materialsymbols.roundedfilled.Translate
import tachiyomi.presentation.core.components.material.IconButtonTokens

enum class ChapterTranslationState {
    NOT_TRANSLATED,
    TRANSLATING,
    TRANSLATED,
}

@Composable
fun ChapterTranslationIndicator(
    translationState: ChapterTranslationState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(IconButtonTokens.StateLayerSize),
        contentAlignment = Alignment.Center,
    ) {
        when (translationState) {
            ChapterTranslationState.NOT_TRANSLATED -> {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Translate,
                        contentDescription = "Translate Chapter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            ChapterTranslationState.TRANSLATING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ChapterTranslationState.TRANSLATED -> {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = MaterialSymbols.RoundedFilled.Translate,
                        contentDescription = "Translated",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
