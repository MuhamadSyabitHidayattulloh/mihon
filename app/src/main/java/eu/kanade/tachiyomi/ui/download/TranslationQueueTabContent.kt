package eu.kanade.tachiyomi.ui.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.model.TranslationTask
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.PlayArrow
import mihon.icons.materialsymbols.roundedfilled.Pause
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun TranslationQueueTabContent(
    translationManager: TranslationManager,
    modifier: Modifier = Modifier,
) {
    val queue by translationManager.queue.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Antrean Terjemahan (${queue.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { translationManager.resumeQueue() }) {
                Icon(imageVector = MaterialSymbols.Rounded.PlayArrow, contentDescription = "Mulai")
            }
            IconButton(onClick = { translationManager.pauseQueue() }) {
                Icon(imageVector = MaterialSymbols.RoundedFilled.Pause, contentDescription = "Jeda")
            }
            IconButton(onClick = { translationManager.clearQueue() }) {
                Icon(imageVector = MaterialSymbols.Rounded.Delete, contentDescription = "Bersihkan")
            }
        }

        if (queue.isEmpty()) {
            EmptyScreen(
                message = "Tidak ada antrean terjemahan",
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(queue) { index, task ->
                    TranslationTaskItem(
                        task = task,
                        onDelete = { translationManager.removeFromQueue(task.chapter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationTaskItem(
    task: TranslationTask,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.manga.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = task.chapter.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val statusText = when (task.state) {
                    TranslationTask.State.QUEUE -> "Dalam Antrean"
                    TranslationTask.State.TRANSLATING -> "Menerjemahkan ${(task.progress * 100).toInt()}%"
                    TranslationTask.State.TRANSLATED -> "Selesai"
                    TranslationTask.State.ERROR -> "Gagal: ${task.error ?: ""}"
                    TranslationTask.State.NOT_TRANSLATED -> ""
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = MaterialSymbols.Rounded.Delete, contentDescription = "Hapus")
            }
        }
    }
}
