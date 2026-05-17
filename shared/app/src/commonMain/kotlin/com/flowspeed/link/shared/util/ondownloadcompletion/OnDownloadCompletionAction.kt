package com.flowspeed.link.shared.util.ondownloadcompletion

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem

interface OnDownloadCompletionAction {
    suspend fun onDownloadCompleted(downloadItem: IDownloadItem)
}
