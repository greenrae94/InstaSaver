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

        // Anything other than a successful save needs to actually reach the user; a save itself stays silent
        manager.createNotificationChannel(
            NotificationChannel(PROBLEM_CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun progress() = notify(CHANNEL_ID, "Saving…", null)

    fun result(outcome: Outcome) {
        val channel = if (OutcomeMessages.needsAttention(outcome)) PROBLEM_CHANNEL_ID else CHANNEL_ID

        notify(channel, OutcomeMessages.title(outcome), OutcomeMessages.text(outcome))
    }

    // Covers exceptions the coordinator doesn't itself catch, so a "Saving…" notification never sits unresolved
    fun failure(detail: String) = notify(PROBLEM_CHANNEL_ID, "Something went wrong", detail)

    private fun notify(channelId: String, title: String, text: String?) {
        val notification = NotificationCompat.Builder(context, channelId)
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
        private const val PROBLEM_CHANNEL_ID = "problems"
    }
}
