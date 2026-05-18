package com.flowspeed.link.shared.singledownloadpage

import com.flowspeed.link.resources.Res
import com.flowspeed.lib.downloader.downloaditem.DownloadJobStatus
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.lib.downloader.monitor.statusOrFinished
import com.flowspeed.lib.downloader.utils.ExceptionUtils
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource

fun createStatusString(it: IDownloadItemState): StringSource {
    if (it is ProcessingDownloadItemState && it.isWaiting) {
        return Res.string.waiting.asStringSource()
    }
    return when (val status = it.statusOrFinished()) {
        is DownloadJobStatus.Canceled -> {
            if (ExceptionUtils.isNormalCancellation(status.e)) {
                Res.string.paused
            } else {
                Res.string.error
            }
        }

        DownloadJobStatus.Downloading -> Res.string.downloading
        DownloadJobStatus.Finished -> Res.string.finished
        DownloadJobStatus.IDLE -> Res.string.idle
        is DownloadJobStatus.PreparingFile -> Res.string.preparing_file
        DownloadJobStatus.Resuming -> Res.string.resuming
        is DownloadJobStatus.Retrying -> Res.string.retrying
    }.asStringSource()
}
