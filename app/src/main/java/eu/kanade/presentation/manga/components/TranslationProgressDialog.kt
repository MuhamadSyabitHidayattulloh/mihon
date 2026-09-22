package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tachiyomi.domain.translation.model.TranslationProgress
import tachiyomi.domain.translation.model.TranslationState

@Composable
fun TranslationProgressDialog(
    progress: TranslationProgress,
    onDismissRequest: () -> Unit,
    onReTranslate: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Status Terjemahan") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tahap saat ini: ${progress.currentStep.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (progress.totalPages > 0) {
                    val floatProgress = progress.progressPagesDone.toFloat() / progress.totalPages
                    LinearProgressIndicator(
                        progress = { floatProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Progress: ${progress.progressPagesDone} / ${progress.totalPages} Halaman",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Done: ${progress.successCount}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Queue: ${progress.queueCount}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Failed: ${progress.failedCount}", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Log Aktivitas:", style = MaterialTheme.typography.labelMedium)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    items(progress.logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Tutup")
            }
        },
        dismissButton = {
            Row {
                if (progress.state == TranslationState.TRANSLATED || progress.state == TranslationState.ERROR) {
                    TextButton(onClick = onDelete) {
                        Text(text = "Hapus")
                    }
                    TextButton(onClick = onReTranslate) {
                        Text(text = "Terjemahkan Ulang")
                    }
                }
            }
        },
    )
}
