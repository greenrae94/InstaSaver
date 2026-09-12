package com.raegreen.instasaver

import androidx.work.WorkInfo
import java.util.UUID

enum class DownloadStage { WAITING, DOWNLOADING }

data class ActiveDownload(val id: UUID, val label: String, val stage: DownloadStage)

object DownloadStages {

    // Only unfinished work earns a row: a finished download becomes a file in the list below,
    // and a failure has already notified
    fun stageOf(state: WorkInfo.State): DownloadStage? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStage.WAITING
        WorkInfo.State.RUNNING -> DownloadStage.DOWNLOADING
        else -> null
    }

    fun activeDownloads(infos: List<WorkInfo>): List<ActiveDownload> = infos.mapNotNull { info ->
        val stage = stageOf(info.state) ?: return@mapNotNull null
        val label = info.progress.getString(DownloadWorker.KEY_LABEL) ?: DEFAULT_LABEL

        ActiveDownload(info.id, label, stage)
    }

    const val DEFAULT_LABEL = "Instagram post"
}
