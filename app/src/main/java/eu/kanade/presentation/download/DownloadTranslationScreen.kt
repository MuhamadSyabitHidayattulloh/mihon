package eu.kanade.presentation.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

object DownloadTranslationScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.label_download_translation),
                    navigateUp = navigator::pop,
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.action_resume)) },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = { /* TODO */ },
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                // For now, show an empty screen
                EmptyScreen(stringRes = MR.strings.information_no_downloads)
            }
        }
    }
}
