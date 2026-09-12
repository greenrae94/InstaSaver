package com.raegreen.instasaver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// Each run supplies its own notification id, so two shares in flight at once never overwrite
// one another's "Saving…" or result — only a run's own progress notification gets replaced by
// its own result.
class Notifier(private val context: Context, private val notificationId: Int) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun progress() = notify("Saving…", null)

    fun result(outcome: Outcome) {
        val (title, text) = when (outcome) {
            is Outcome.Saved -> "Saved" to if (outcome.count == 1) "1 item saved to your gallery" else "${outcome.count} items saved to your gallery"
            Outcome.AlreadySaved -> "Already saved" to "This post is already in your gallery"
            Outcome.NotAPostLink -> "Not a post link" to "That's not a post or Reel link"
            is Outcome.CouldNotRead -> "Couldn't read this post" to "Instagram didn't return the post data"
            is Outcome.NetworkFailed -> "Download failed" to outcome.reason
        }

        notify(title, text)
    }

    // Covers exceptions the coordinator doesn't itself catch, so a "Saving…" notification never sits unresolved
    fun failure(detail: String) = notify("Something went wrong", detail)

    private fun notify(title: String, text: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(notificationId, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
    }
}
