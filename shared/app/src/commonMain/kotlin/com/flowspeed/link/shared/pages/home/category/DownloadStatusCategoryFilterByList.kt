package com.flowspeed.link.shared.pages.home.category

import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.monitor.statusOrFinished
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource

class DownloadStatusCategoryFilterByList(
    name: StringSource,
    icon: IconSource,
    val acceptedStatus: List<DownloadStatus>,
) : DownloadStatusCategoryFilter(name, icon) {
    override fun accept(iDownloadStatus: IDownloadItemState): Boolean {
        return iDownloadStatus
            .statusOrFinished()
            .asDownloadStatus() in acceptedStatus
    }
}
