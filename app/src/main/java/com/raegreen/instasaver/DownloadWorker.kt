package com.raegreen.instasaver

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sharedText = inputData.getString(KEY_TEXT)
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)
        val notifier = Notifier(applicationContext, notificationId)

        notifier.progress()

        val client = OkHttpClient()
        val resolver = MediaResolver(InstagramPageFetcher(client))
        val downloader = MediaDownloader(client)
        val saver = GallerySaver(applicationContext)

        val source = object : PostSource {
            override fun resolve(target: LinkTarget) = resolver.resolve(target)
            override fun download(url: String) = downloader.download(url)
        }

        val sink = object : MediaSink {
            override fun alreadySaved(fileName: String, type: MediaType) = saver.alreadySaved(fileName, type)
            override fun save(fileName: String, type: MediaType, source: InputStream) {
                saver.save(fileName, type, source)
            }
        }

        // The coordinator only catches PostDataParseException and IOException; anything else (a
        // SecurityException out of ContentResolver, say) must still resolve the "Saving…" notification
        // rather than leave it on screen forever
        val outcome = try {
            DownloadCoordinator(source, sink).run(sharedText) { label ->
                setProgress(androidx.work.workDataOf(KEY_LABEL to label))
            }
        } catch (error: CancellationException) {
            // CancellationException is how WorkManager stops this worker (e.g. constraints no longer
            // hold); it must propagate untouched rather than being reported as an app failure below
            throw error
        } catch (error: Exception) {
            Log.d(LOG_TAG, "unexpected failure for ${sharedText?.take(80)}: $error")
            notifier.failure("Something went wrong while saving this post")

            return@withContext Result.failure()
        }

        Log.d(LOG_TAG, "outcome for ${sharedText?.take(80)}: $outcome")

        // runAttemptCount starts at 0 and only increments after a retry, so capping at < 2 lets attempts
        // 0, 1 and 2 run (three attempts total) before the next one is left to fail for good
        if (outcome is Outcome.NetworkFailed && runAttemptCount < 2) return@withContext Result.retry()

        notifier.result(outcome)

        // A NetworkFailed outcome that has exhausted its retries is a genuine failure, not successful
        // work, even though the user has already been told via the notification above
        if (outcome is Outcome.NetworkFailed) Result.failure() else Result.success()
    }

    companion object {
        const val KEY_TEXT = "shared_text"
        const val KEY_LABEL = "label"
        const val TAG = "instasaver-download"
        private const val KEY_NOTIFICATION_ID = "notification_id"
        private const val DEFAULT_NOTIFICATION_ID = 1
        private const val LOG_TAG = "InstaSaver"

        fun enqueue(context: Context, sharedText: String) {
            // A fresh id per share means concurrent runs each own their notification instead of
            // clobbering one another's progress/result
            val notificationId = System.currentTimeMillis().toInt()

            val data = Data.Builder()
                .putString(KEY_TEXT, sharedText)
                .putInt(KEY_NOTIFICATION_ID, notificationId)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
