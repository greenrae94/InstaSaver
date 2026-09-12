package com.raegreen.instasaver

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ItemScreen(item: SavedItem, onBack: () -> Unit) {
    val context = LocalContext.current

    // Android asks the user to confirm deleting files this app did not create
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) onBack()
    }

    var deleteFailed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }

        Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2)

        if (item.type == MediaType.VIDEO) {
            VideoPlayer(item, Modifier.weight(1f))
        } else {
            ImageView(item, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { context.startActivity(Intent.createChooser(ItemActions.shareIntent(item), null)) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Share")
            }
            Button(
                onClick = {
                    // The item stays open on failure so the message below is visible, rather than
                    // closing a screen that no longer matches what actually happened
                    when (val result = ItemActions.deleteOrRequest(context, item.uri)) {
                        is ItemActions.DeleteResult.Deleted -> onBack()

                        is ItemActions.DeleteResult.NeedsConfirmation ->
                            deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())

                        is ItemActions.DeleteResult.Failed -> deleteFailed = true
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }

        if (deleteFailed) {
            Text("Couldn't delete this file", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VideoPlayer(item: SavedItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var failed by remember(item.uri) { mutableStateOf(false) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    // A file that will not play must say so rather than showing a silent black rectangle
    DisposableEffect(item.uri) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Sharing to Discord backgrounds the app without closing this screen — pause rather than
    // let audio keep playing behind another app. Left paused on return; no auto-resume.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (failed) {
        Text("Couldn't play this file", modifier = modifier)
    } else {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImageView(item: SavedItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }

    bitmap?.let {
        Image(bitmap = it, contentDescription = item.displayName, modifier = modifier.fillMaxWidth())
    } ?: Text("Couldn't open this image", modifier = modifier)
}
