package com.flowspeed.link.shared.pages.home.category

import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource

abstract class DownloadStatusCategoryFilter(
    val name: StringSource,
    val icon: IconSource,
) {
    abstract fun accept(iDownloadStatus: IDownloadItemState): Boolean
}
