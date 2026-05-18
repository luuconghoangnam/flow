package com.flowspeed.link.shared.util.ondownloadcompletion

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem

interface OnDownloadCompletionActionProvider {
    suspend fun getOnDownloadCompletionAction(downloadItem: IDownloadItem): List<OnDownloadCompletionAction>
}

class NoOpOnDownloadCompletionActionProvider : OnDownloadCompletionActionProvider {
    override suspend fun getOnDownloadCompletionAction(downloadItem: IDownloadItem): List<OnDownloadCompletionAction> {
        return emptyList()
    }
}
