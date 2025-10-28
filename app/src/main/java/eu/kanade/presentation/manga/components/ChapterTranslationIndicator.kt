
package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.translation.model.Translation
import tachiyomi.presentation.core.components.material.IconToggleButton

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    translationStateProvider: () -> Translation.State,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (enabled) {
        val state = translationStateProvider()
        val icon = when (state) {
            Translation.State.NOT_TRANSLATED -> Icons.Outlined.Translate
            Translation.State.TRANSLATING -> Icons.Outlined.Translate // TODO: Add a loading indicator
            Translation.State.TRANSLATED -> Icons.Outlined.Translate // TODO: Add a checkmark icon
        }
        IconToggleButton(
            checked = false,
            onCheckedChange = { onClick() },
            modifier = modifier.padding(start = 4.dp),
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
            },
        )
    }
}
