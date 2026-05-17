package com.flowspeed.link.android.util

import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.util.osfileutil.FileUtils
import java.io.File

class AndroidDownloadItemOpener(
    private val downloadSystem: DownloadSystem
) : DownloadItemOpener {
    override suspend fun openDownloadItem(id: Long) {
        downloadSystem.getDownloadItemById(id)?.let {
            openDownloadItem(it)
        }
    }

    override suspend fun openDownloadItem(downloadItem: IDownloadItem) {
        try {
            FileUtils.openFile(File(downloadItem.folder, downloadItem.name))
        } catch (e: Exception) {
            // toast something
        }
    }

    override suspend fun openDownloadItemFolder(id: Long) {
        downloadSystem.getDownloadItemById(id)?.let {
            openDownloadItemFolder(it)
        }
    }

    override suspend fun openDownloadItemFolder(downloadItem: IDownloadItem) {
        try {
            FileUtils.openFolderOfFile(File(downloadItem.folder, downloadItem.name))
        } catch (e: Exception) {
            // toast something
        }
    }
}
