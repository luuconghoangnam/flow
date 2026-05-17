package com.flowspeed.link.shared.pages.home.category

import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.util.compose.asStringSource

object DefinedStatusCategories {
    fun values() = listOf(All, Finished, Unfinished)


    val All = object : DownloadStatusCategoryFilter(
        Res.string.all.asStringSource(),
        MyIcons.folder,
    ) {
        override fun accept(iDownloadStatus: IDownloadItemState): Boolean = true
    }
    val Finished = DownloadStatusCategoryFilterByList(
        Res.string.finished.asStringSource(),
        MyIcons.folderFinished,
        listOf(DownloadStatus.Completed)
    )
    val Unfinished = DownloadStatusCategoryFilterByList(
        Res.string.Unfinished.asStringSource(),
        MyIcons.folderUnfinished,
        listOf(
            DownloadStatus.Error,
            DownloadStatus.Added,
            DownloadStatus.Paused,
            DownloadStatus.Downloading,
        )
    )
}
