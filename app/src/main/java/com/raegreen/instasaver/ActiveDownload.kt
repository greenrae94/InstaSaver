package com.raegreen.instasaver

import android.content.Context
import androidx.work.WorkInfo
import java.util.UUID

// Shared by every SharedPreferences-backed store in the app, so a typo in the file name cannot
// silently split what is meant to be one store into two
const val PREFERENCES_NAME = "instasaver"

enum class DownloadStage { WAITING, DOWNLOADING, FAILED }

data class ActiveDownload(val id: UUID, val label: String, val stage: DownloadStage, val reason: String? = null)

object DownloadStages {

    // Only unfinished or failed work earns a row: a finished download becomes a file in the
    // list below, but a failure needs to be seen rather than silently disappear
    fun stageOf(state: WorkInfo.State): DownloadStage? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStage.WAITING
        WorkInfo.State.RUNNING -> DownloadStage.DOWNLOADING
        WorkInfo.State.FAILED -> DownloadStage.FAILED
        else -> null
    }

    fun activeDownloads(infos: List<WorkInfo>): List<ActiveDownload> = infos.mapNotNull { info ->
        val stage = stageOf(info.state) ?: return@mapNotNull null

        // Output data first: progress data is gone by the time work reaches a terminal state,
        // so a failed row's label survives only if the worker also wrote it to output data
        val label = info.outputData.getString(DownloadWorker.KEY_LABEL)
            ?: info.progress.getString(DownloadWorker.KEY_LABEL)
            ?: DEFAULT_LABEL
        val reason = if (stage == DownloadStage.FAILED) info.outputData.getString(DownloadWorker.KEY_ERROR) else null

        ActiveDownload(info.id, label, stage, reason)
    }

    const val DEFAULT_LABEL = "Instagram post"
}

// WorkManager has no API to delete a single finished work record — pruneWork() removes all of
// them — so which failed rows the user has dismissed has to be the app's own persisted state.
// Persisted rather than in-memory so a dismissal survives reopening the app.
class DismissedFailures(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // SharedPreferences.getStringSet hands back the live internal Set instance rather than a
    // copy, and mutating it in place corrupts the store — so always return a fresh copy
    fun get(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun add(id: String) {
        preferences.edit().putStringSet(KEY, get() + id).apply()
    }

    // Terminal work records expire from WorkManager after about a week, at which point a
    // dismissal stored against that id is meaningless. Only ids still present among the
    // currently reported work are worth keeping.
    fun prune(activeIds: Set<String>) {
        val current = get()
        val kept = current.intersect(activeIds)

        if (kept.size != current.size) {
            preferences.edit().putStringSet(KEY, kept).apply()
        }
    }

    private companion object {
        const val KEY = "dismissed_failures"
    }
}
