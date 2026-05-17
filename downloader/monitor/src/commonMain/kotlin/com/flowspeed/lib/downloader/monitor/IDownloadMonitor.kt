package com.flowspeed.lib.downloader.monitor

import com.flowspeed.lib.downloader.downloaditem.DownloadJobStatus
import com.flowspeed.lib.util.flow.mapStateFlow
import kotlinx.coroutines.flow.StateFlow

interface IDownloadMonitor {
    var useAverageSpeed: Boolean
    val activeDownloadListFlow: StateFlow<List<ProcessingDownloadItemState>>
    val completedDownloadListFlow: StateFlow<List<CompletedDownloadItemState>>
    val downloadListFlow: StateFlow<List<IDownloadItemState>>
    val activeDownloadCount: StateFlow<Int>

    suspend fun waitForDownloadToFinishOrCancel(
        id: Long,
    )
}

fun IDownloadMonitor.isDownloadActiveFlow(
    downloadId: Long,
): StateFlow<Boolean> {
    return activeDownloadListFlow.mapStateFlow { activeDownloadList ->
        activeDownloadList.find {
            downloadId == it.id
        }?.canBePaused() ?: false
    }
}
