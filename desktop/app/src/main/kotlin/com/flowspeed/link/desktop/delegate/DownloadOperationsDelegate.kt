package com.flowspeed.link.desktop.delegate

import com.flowspeed.lib.downloader.NewDownloadItemProps
import com.flowspeed.lib.downloader.destination.IncompleteFileUtil
import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.queue.DefaultQueueInfo
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.coroutines.launchWithDeferred
import com.flowspeed.lib.util.osfileutil.FileUtils
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.pagemanager.NotificationSender
import com.flowspeed.link.shared.ui.widget.NotificationType
import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.category.CategorySelectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles download CRUD operations and file opening.
 *
 * Extracted from AppComponent to isolate download business logic
 * from navigation concerns.
 */
class DownloadOperationsDelegate(
    private val scope: CoroutineScope,
    private val downloadSystem: DownloadSystem,
    private val notificationSender: NotificationSender,
) : DownloadItemOpener {

    /** Adds multiple downloads, optionally to a queue and/or category. */
    fun addDownloads(
        items: List<NewDownloadItemProps>,
        categorySelectionMode: CategorySelectionMode?,
        queueId: Long?,
    ): Deferred<List<Long>> {
        return scope.launchWithDeferred {
            downloadSystem.addDownload(
                newItemsToAdd = items,
                queueId = queueId,
                categorySelectionMode = categorySelectionMode,
            )
        }
    }

    /** Adds a single download to a queue and/or category. */
    fun addDownload(
        item: NewDownloadItemProps,
        queueId: Long?,
        categoryId: Long?,
    ): Deferred<Long> {
        return scope.launchWithDeferred {
            downloadSystem.addDownload(
                newDownload = item,
                queueId = queueId,
                categoryId = categoryId,
            )
        }
    }

    /** Adds a download and immediately starts it. */
    fun startNewDownload(
        item: NewDownloadItemProps,
        categoryId: Long?,
    ): Deferred<Long> {
        return scope.launchWithDeferred {
            downloadSystem.addDownload(
                newDownload = item,
                queueId = DefaultQueueInfo.ID,
                categoryId = categoryId,
            ).also {
                downloadSystem.userManualResume(it)
            }
        }
    }

    override suspend fun openDownloadItem(id: Long) {
        val item = downloadSystem.getDownloadItemById(id)
        if (item == null) {
            notificationSender.sendNotification(
                Res.string.open_file,
                Res.string.cant_open_file.asStringSource(),
                Res.string.download_item_not_found.asStringSource(),
                NotificationType.Error,
            )
            return
        }
        openDownloadItem(item)
    }

    override suspend fun openDownloadItem(downloadItem: IDownloadItem) {
        runCatching {
            withContext(Dispatchers.IO) {
                FileUtils.openFile(downloadSystem.getDownloadFile(downloadItem))
            }
        }.onFailure {
            notificationSender.sendNotification(
                Res.string.open_file,
                Res.string.cant_open_file.asStringSource(),
                it.localizedMessage?.asStringSource() ?: Res.string.unknown_error.asStringSource(),
                NotificationType.Error,
            )
        }
    }

    override suspend fun openDownloadItemFolder(id: Long) {
        val item = downloadSystem.getDownloadItemById(id)
        if (item == null) {
            notificationSender.sendNotification(
                Res.string.open_folder,
                Res.string.cant_open_folder.asStringSource(),
                Res.string.download_item_not_found.asStringSource(),
                NotificationType.Error,
            )
            return
        }
        openDownloadItemFolder(item)
    }

    override suspend fun openDownloadItemFolder(downloadItem: IDownloadItem) {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = downloadSystem.getDownloadFile(downloadItem)
                if (file.exists()) {
                    FileUtils.openFolderOfFile(file)
                } else {
                    val incompleteFile = IncompleteFileUtil.addIncompleteIndicator(file, downloadItem.id)
                    if (incompleteFile.exists() && downloadItem.status != DownloadStatus.Completed) {
                        FileUtils.openFolderOfFile(incompleteFile)
                    } else {
                        FileUtils.openFolder(file.parentFile)
                    }
                }
            }
        }.onFailure {
            notificationSender.sendNotification(
                Res.string.open_folder,
                Res.string.cant_open_folder.asStringSource(),
                it.localizedMessage?.asStringSource() ?: Res.string.unknown_error.asStringSource(),
                NotificationType.Error,
            )
        }
    }
}
