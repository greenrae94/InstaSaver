package com.raegreen.instasaver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

// Invisible on purpose: it hands the text to the worker and closes, so Instagram stays on screen
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)

        if (!sharedText.isNullOrBlank()) {
            DownloadWorker.enqueue(applicationContext, sharedText)
        }

        finish()
    }
}
