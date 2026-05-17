package com.flowspeed.link.desktop.actions.onevennts

import com.flowspeed.link.shared.storage.IExtraDownloadSettingsStorage
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionAction
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem

class CleanExtraSettingsOnDownloadFinish(
    private val storage: IExtraDownloadSettingsStorage<*>
) : OnDownloadCompletionAction {
    override suspend fun onDownloadCompleted(downloadItem: IDownloadItem) {
        storage.deleteExtraDownloadItemSettings(downloadItem.id)
    }
}
