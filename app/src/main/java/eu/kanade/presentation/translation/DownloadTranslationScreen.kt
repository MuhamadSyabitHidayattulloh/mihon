package eu.kanade.presentation.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.Screen

object DownloadTranslationScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        // Dummy data
        val downloadItems = List(5) { index ->
            DownloadTranslationUiModel(
                mangaTitle = "Manga Title ${index + 1}",
                chapterName = "Chapter ${100 + index}",
                progress = (index + 1) * 20,
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(MR.strings.label_download_translation)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(MR.strings.action_bar_up_description),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier.padding(contentPadding),
            ) {
                items(downloadItems.size) { index ->
                    DownloadTranslationItem(item = downloadItems[index])
                }
            }
        }
    }
}

@Composable
private fun DownloadTranslationItem(item: DownloadTranslationUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.mangaTitle)
            Text(text = item.chapterName)
        }
        Column(modifier = Modifier.weight(0.4f), horizontalAlignment = Alignment.End) {
            Text(text = "${item.progress}%")
            LinearProgressIndicator(
                progress = { item.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class DownloadTranslationUiModel(
    val mangaTitle: String,
    val chapterName: String,
    val progress: Int,
)
