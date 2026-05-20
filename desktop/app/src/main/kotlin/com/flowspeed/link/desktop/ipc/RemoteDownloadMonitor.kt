package com.flowspeed.link.desktop.ipc

import com.flowspeed.lib.downloader.downloaditem.DownloadJobStatus
import com.flowspeed.lib.downloader.monitor.*
import com.flowspeed.lib.downloader.part.PartDownloadStatus
import com.flowspeed.link.service.ipc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

class RemoteDownloadMonitor(
    private val remoteDownloadSystem: RemoteDownloadSystem,
    private val scope: CoroutineScope,
) : IDownloadMonitor {
    override var useAverageSpeed: Boolean = false

    override val activeDownloadListFlow: StateFlow<List<ProcessingDownloadItemState>> = remoteDownloadSystem.downloads
        .map { list ->
            list.filter { it.status != "Completed" }
                .map { item ->
                    val jobStatus = when (item.status) {
                        "Downloading" -> DownloadJobStatus.Downloading
                        "Paused" -> DownloadJobStatus.Canceled(Exception("Paused"))
                        "Error" -> DownloadJobStatus.Canceled(Exception("Error"))
                        else -> DownloadJobStatus.IDLE
                    }
                    val fakePart = UiRangedPart(
                        id = 0,
                        status = PartDownloadStatus.ReceivingData,
                        howMuchProceed = item.downloadedSize,
                        percent = null,
                        length = item.contentLength,
                        partSpace = 1.0f
                    )
                    RangeBasedProcessingDownloadItemState(
                        id = item.id,
                        folder = item.folder,
                        name = item.name,
                        downloadLink = item.url,
                        contentLength = item.contentLength,
                        saveLocation = item.name,
                        dateAdded = item.dateAdded,
                        startTime = item.startTime ?: -1,
                        completeTime = item.completeTime ?: -1,
                        status = jobStatus,
                        speed = item.speed,
                        parts = listOf(fakePart),
                        supportResume = true,
                        isWaiting = false,
                    )
                }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val completedDownloadListFlow: StateFlow<List<CompletedDownloadItemState>> = remoteDownloadSystem.downloads
        .map { list ->
            list.filter { it.status == "Completed" }
                .map { item ->
                    CompletedDownloadItemState(
                        id = item.id,
                        folder = item.folder,
                        name = item.name,
                        downloadLink = item.url,
                        contentLength = item.contentLength,
                        saveLocation = item.name,
                        dateAdded = item.dateAdded,
                        startTime = item.startTime ?: -1,
                        completeTime = item.completeTime ?: -1,
                    )
                }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val downloadListFlow: StateFlow<List<IDownloadItemState>> = activeDownloadListFlow
        .combine(completedDownloadListFlow) { active, completed -> active + completed }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val activeDownloadCount: StateFlow<Int> = remoteDownloadSystem.activeDownloadCount

    override suspend fun waitForDownloadToFinishOrCancel(id: Long) {
        while (true) {
            val item = remoteDownloadSystem.downloads.value.find { it.id == id }
            if (item == null || item.status == "Completed" || item.status == "Error" || item.status == "Paused") {
                break
            }
            kotlinx.coroutines.delay(500)
        }
    }
}
