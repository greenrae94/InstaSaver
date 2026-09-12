package com.raegreen.instasaver

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStagesTest {

    @Test
    fun `queued work is waiting`() {
        assertEquals(DownloadStage.WAITING, DownloadStages.stageOf(WorkInfo.State.ENQUEUED))
        assertEquals(DownloadStage.WAITING, DownloadStages.stageOf(WorkInfo.State.BLOCKED))
    }

    @Test
    fun `running work is downloading`() {
        assertEquals(DownloadStage.DOWNLOADING, DownloadStages.stageOf(WorkInfo.State.RUNNING))
    }

    @Test
    fun `finished work gets no row`() {
        assertNull(DownloadStages.stageOf(WorkInfo.State.SUCCEEDED))
        assertNull(DownloadStages.stageOf(WorkInfo.State.FAILED))
        assertNull(DownloadStages.stageOf(WorkInfo.State.CANCELLED))
    }
}
