package com.raegreen.instasaver

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.flow.map

@Composable
fun DownloadsScreen(onOpenItem: (SavedItem) -> Unit) {
    val context = LocalContext.current
    val repository = remember { GalleryRepository(context) }
    val dismissedFailures = remember { DismissedFailures(context) }

    var dismissed by remember { mutableStateOf(dismissedFailures.get()) }

    // Saved files come from the gallery; live rows come from the work queue
    val items by remember { repository.items() }.collectAsStateWithLifecycle(emptyList())
    val active by remember {
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(DownloadWorker.TAG)
            .map { DownloadStages.activeDownloads(it) }
    }.collectAsStateWithLifecycle(emptyList())

    // Dismissal is per-row and persisted, since WorkManager can only prune every finished
    // record at once, which would silently wipe a sibling failure the owner hasn't read yet
    val visibleActive = active.filterNot { dismissed.contains(it.id.toString()) }

    // Once a work record expires from WorkManager it stops appearing here, so any dismissed id
    // no longer among the active ones is stale. Only prune on a genuinely non-empty list — the
    // flow's initial value is an empty list before WorkManager has reported anything, and
    // pruning against that would wipe the whole set and resurrect every dismissed row.
    LaunchedEffect(active) {
        if (active.isNotEmpty()) {
            dismissedFailures.prune(active.map { it.id.toString() }.toSet())
            dismissed = dismissedFailures.get()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PasteRow()

        if (items.isEmpty() && visibleActive.isEmpty()) {
            Text(
                "Nothing saved yet. In Instagram, tap Share and pick InstaSaver — or paste a link above.",
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(visibleActive, key = { it.id.toString() }) { download ->
                ActiveRow(
                    download,
                    onDismiss = {
                        dismissedFailures.add(download.id.toString())
                        dismissed = dismissedFailures.get()
                    },
                )
            }
            items(items, key = { it.uri.toString() }) { item ->
                SavedRow(item, onClick = { onOpenItem(item) })
            }
        }
    }
}

@Composable
private fun PasteRow() {
    val context = LocalContext.current

    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = link,
            onValueChange = {
                link = it
                error = null
            },
            label = { Text("Paste a link") },
            isError = error != null,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                if (LinkParser.parse(link) == null) {
                    error = "Not a post link"
                } else {
                    DownloadWorker.enqueue(context, link)
                    link = ""
                }
            },
            enabled = link.isNotBlank(),
        ) {
            Text("Save")
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ActiveRow(download: ActiveDownload, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (download.stage == DownloadStage.FAILED) it.clickable(onClick = onDismiss) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (download.stage == DownloadStage.FAILED) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        Column {
            Text(download.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (download.stage) {
                    DownloadStage.WAITING -> "Waiting…"
                    DownloadStage.DOWNLOADING -> "Downloading…"
                    DownloadStage.FAILED -> download.reason ?: "Download failed"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (download.stage == DownloadStage.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (download.stage == DownloadStage.FAILED) {
                Text("Tap to dismiss", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, item.uri) {
        value = Thumbnails.load(context, item)
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            thumbnail?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            } ?: Icon(Icons.Filled.PlayArrow, contentDescription = null)
        }
        Column {
            Text(item.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${DateUtils.getRelativeTimeSpanString(item.dateAddedSeconds * 1000)} · ${Formatter.formatShortFileSize(context, item.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
