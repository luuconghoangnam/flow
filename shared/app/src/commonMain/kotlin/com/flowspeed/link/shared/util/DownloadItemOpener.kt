package com.flowspeed.link.shared.util

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem

interface DownloadItemOpener {
    suspend fun openDownloadItem(id:Long)
    suspend fun openDownloadItem(downloadItem: IDownloadItem)

    suspend fun openDownloadItemFolder(id:Long)
    suspend fun openDownloadItemFolder(downloadItem: IDownloadItem)
}
