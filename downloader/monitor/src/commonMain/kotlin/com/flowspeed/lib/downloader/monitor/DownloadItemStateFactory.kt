package com.flowspeed.lib.downloader.monitor

import androidx.compose.runtime.Immutable
import com.flowspeed.lib.downloader.downloaditem.DownloadJob
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem

@Immutable
data class ProcessingDownloadItemFactoryInputs<
        out TDownloadJob : DownloadJob
        >(
    val downloadJob: TDownloadJob,
    val speed: Long,
    val isWaiting: Boolean,
)

interface DownloadItemStateFactory<
        in TDownloadItem : IDownloadItem,
        in TDownloadJob : DownloadJob
        > {
    fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<TDownloadJob>
    ): ProcessingDownloadItemState

    fun createCompletedDownloadItemState(
        downloadItem: TDownloadItem,
    ): CompletedDownloadItemState

}
