package com.raegreen.instasaver

import android.Manifest
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var openItem by remember { mutableStateOf<SavedItem?>(null) }

                    NotificationPermissionRequest()

                    val item = openItem

                    // Inset content around the status bar, nav bar, camera cutout and rounded
                    // corners once here so every screen below gets it for free
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        if (item == null) {
                            DownloadsScreen(onOpenItem = { openItem = it })
                        } else {
                            ItemScreen(item = item, onBack = { openItem = null })
                        }
                    }
                }
            }
        }
    }

    // Android refuses clipboard reads until the window actually has focus, which happens
    // after onResume — reading any earlier is silently denied
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus) return

        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val store = LastHandledLink(this)
        val link = ClipboardPickup.linkToDownload(text, store.get()) ?: return

        Log.d("InstaSaver", "clipboard pickup: ${link.take(60)}")

        store.set(link)

        DownloadWorker.enqueue(this, link)
    }
}

@Composable
private fun NotificationPermissionRequest() {
    // Android 13+ will not show notifications until the user allows them
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
