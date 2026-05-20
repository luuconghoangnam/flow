/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.shared.util

import com.flowspeed.link.shared.storage.IExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.IExtraQueueSettingsStorage
import com.flowspeed.link.shared.util.category.CategoryItemWithId
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.category.CategorySelectionMode
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionRunner
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueEventActionRunner
import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.NewDownloadItemProps
import com.flowspeed.lib.downloader.db.IDownloadListDb
import com.flowspeed.lib.downloader.downloaditem.*
import com.flowspeed.lib.downloader.downloaditem.contexts.ResumedBy
import com.flowspeed.lib.downloader.downloaditem.contexts.StoppedBy
import com.flowspeed.lib.downloader.downloaditem.contexts.User
import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.monitor.IDownloadMonitor
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.lib.downloader.monitor.isDownloadActiveFlow
import com.flowspeed.lib.downloader.queue.ManualDownloadQueue
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.downloader.utils.OnDuplicateStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * a facade for download manager library
 * all the download manager features should be accessed and controlled here
 */
interface IRemoteDownloadDelegate {
    suspend fun addDownload(
        newItemsToAdd: List<NewDownloadItemProps>,
        queueId: Long?,
        categorySelectionMode: CategorySelectionMode?,
    ): List<Long>
    suspend fun removeDownload(id: Long, alsoRemoveFile: Boolean)
    suspend fun resumeDownload(id: Long): Boolean
    suspend fun pauseDownload(id: Long): Boolean
    suspend fun resetDownload(id: Long): Boolean
    suspend fun startQueue(queueId: Long)
    suspend fun stopQueue(queueId: Long)
}

class DownloadSystem(
    val downloadManager: DownloadManager,
    val queueManager: QueueManager,
    val manualDownloadQueue: ManualDownloadQueue,
    val categoryManager: CategoryManager,
    val downloadMonitor: IDownloadMonitor,
    val onDownloadCompletionActionRunner: OnDownloadCompletionActionRunner,
    val onQueueEventActionRunner: OnQueueEventActionRunner,
    private val scope: CoroutineScope,
    private val downloadListDB: IDownloadListDb,
    private val extraQueueSettingsStorage: IExtraQueueSettingsStorage<*>,
    private val extraDownloadSettingsStorage: IExtraDownloadSettingsStorage<*>,
    private val foldersRegistry: DownloadFoldersRegistry,
    private val remoteDelegate: IRemoteDownloadDelegate? = null,
) {
    private val booted = MutableStateFlow(false)

    val downloadEvents = downloadManager.listOfJobsEvents

    suspend fun boot() {
        if (booted.value) return
        foldersRegistry.boot()
        queueManager.boot()
        downloadManager.boot()
        categoryManager.boot()
        manualDownloadQueue.boot()
        onDownloadCompletionActionRunner.startListening()
        onQueueEventActionRunner.startListening()
        booted.update { true }
    }

    suspend fun addDownload(
        newItemsToAdd: List<NewDownloadItemProps>,
        queueId: Long? = null,
        categorySelectionMode: CategorySelectionMode? = null,
    ): List<Long> {
        val delegate = remoteDelegate
        if (delegate != null) {
            return delegate.addDownload(newItemsToAdd, queueId, categorySelectionMode)
        }
        val createdIds = newItemsToAdd.map {
            downloadManager.addDownload(it)
        }
        createdIds.also { ids ->
            queueId?.let {
                queueManager.addToQueue(
                    it, ids
                )
            }
        }
        categorySelectionMode?.let {
            when (it) {
                CategorySelectionMode.Auto -> {
                    categoryManager.autoAddItemsToCategoriesBasedOnFileNames(
                        createdIds.mapIndexed { index: Int, id: Long ->
                            val downloadItem = newItemsToAdd[index].downloadItem
                            CategoryItemWithId(
                                id = id,
                                fileName = downloadItem.name,
                                url = downloadItem.link,
                            )
                        }
                    )
                }

                is CategorySelectionMode.Fixed -> {
                    categoryManager.addItemsToCategory(
                        it.categoryId,
                        createdIds,
                    )
                }
            }
        }
        return createdIds
    }

    suspend fun addDownload(
        newDownload: NewDownloadItemProps,
        queueId: Long?,
        categoryId: Long?,
    ): Long {
        val delegate = remoteDelegate
        if (delegate != null) {
            return delegate.addDownload(listOf(newDownload), queueId, categoryId?.let { CategorySelectionMode.Fixed(it) }).firstOrNull() ?: -1L
        }
        val downloadId = downloadManager.addDownload(newDownload)
        queueId?.let {
            queueManager.addToQueue(queueId, downloadId)
        }
        categoryId?.let {
            categoryManager.addItemsToCategory(
                categoryId = categoryId,
                itemIds = listOf(downloadId)
            )
        }
        return downloadId
    }

    suspend fun removeDownload(
        id: Long,
        alsoRemoveFile: Boolean,
        context: DownloadItemContext,
    ) {
        val delegate = remoteDelegate
        if (delegate != null) {
            delegate.removeDownload(id, alsoRemoveFile)
            return
        }
        downloadManager.deleteDownload(
            id = id,
            alsoRemoveFile = {
                alsoRemoveFile
            },
            context = context
        )
        categoryManager.removeItemInCategories(listOf(id))
        extraDownloadSettingsStorage.deleteExtraDownloadItemSettings(id)
    }

    suspend fun userManualResume(id: Long): Boolean {
        val delegate = remoteDelegate
        if (delegate != null) {
            return delegate.resumeDownload(id)
        }
        manualDownloadQueue.resume(id)
        return true
    }

    suspend fun manualResume(id: Long, context: DownloadItemContext): Boolean {
        val delegate = remoteDelegate
        if (delegate != null) {
            return delegate.resumeDownload(id)
        }
        // it won't go though headless queue
        // to respect the max concurrent limits
        downloadManager.resume(id, context)
        return true
    }

    suspend fun reset(id: Long): Boolean {
        val delegate = remoteDelegate
        if (delegate != null) {
            return delegate.resetDownload(id)
        }
        downloadManager.reset(id)
        return true
    }

    suspend fun manualPause(id: Long): Boolean {
        val delegate = remoteDelegate
        if (delegate != null) {
            return delegate.pauseDownload(id)
        }
        manualDownloadQueue.pause(id)
        return true
    }

    suspend fun startQueue(
        queueId: Long,
    ) {
        val delegate = remoteDelegate
        if (delegate != null) {
            delegate.startQueue(queueId)
            return
        }
        val queue = queueManager.getQueue(queueId)
        if (queue.isQueueActive) {
            return
        }
//      going to start
        queue.start()
    }

    suspend fun stopAnything() {
        queueManager.getAll().forEach {
            it.stop()
        }
        manualDownloadQueue.clearQueue()
        downloadManager.stopAll()
    }

    suspend fun stopQueue(
        queueId: Long,
    ) {
        val delegate = remoteDelegate
        if (delegate != null) {
            delegate.stopQueue(queueId)
            return
        }
        queueManager.getQueue(queueId)
            .stop()
    }

    suspend fun getDownloadItemById(id: Long): IDownloadItem? {
        val delegate = remoteDelegate
        if (delegate != null) {
            val itemState = downloadMonitor.downloadListFlow.value.find { it.id == id } ?: return null
            return MockDownloadItem(
                id = itemState.id,
                folder = itemState.folder,
                name = itemState.name,
                link = itemState.downloadLink,
                contentLength = itemState.contentLength,
                dateAdded = itemState.dateAdded,
                startTime = itemState.startTime,
                completeTime = itemState.completeTime,
                status = when (itemState) {
                    is com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState -> DownloadStatus.Completed
                    is ProcessingDownloadItemState -> when (itemState.status) {
                        is DownloadJobStatus.Downloading -> DownloadStatus.Downloading
                        is DownloadJobStatus.Finished -> DownloadStatus.Completed
                        else -> DownloadStatus.Paused
                    }
                    else -> DownloadStatus.Added
                }
            )
        }
        return downloadListDB.getById(id) ?: return null
    }

    suspend fun getDownloadItemByLink(link: String): List<IDownloadItem> {
        val delegate = remoteDelegate
        if (delegate != null) {
            return downloadMonitor.downloadListFlow.value
                .filter { it.downloadLink == link }
                .map { itemState ->
                    MockDownloadItem(
                        id = itemState.id,
                        folder = itemState.folder,
                        name = itemState.name,
                        link = itemState.downloadLink,
                        contentLength = itemState.contentLength,
                        dateAdded = itemState.dateAdded,
                        startTime = itemState.startTime,
                        completeTime = itemState.completeTime,
                        status = when (itemState) {
                            is com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState -> DownloadStatus.Completed
                            is ProcessingDownloadItemState -> when (itemState.status) {
                                is DownloadJobStatus.Downloading -> DownloadStatus.Downloading
                                is DownloadJobStatus.Finished -> DownloadStatus.Completed
                                else -> DownloadStatus.Paused
                            }
                            else -> DownloadStatus.Added
                        }
                    )
                }
        }
        return downloadListDB.getAll().filter {
            it.link == link
        }
    }

    suspend fun getDownloadItemsBy(selector: (IDownloadItem) -> Boolean): List<IDownloadItem> {
        val delegate = remoteDelegate
        if (delegate != null) {
            return downloadMonitor.downloadListFlow.value
                .map { itemState ->
                    MockDownloadItem(
                        id = itemState.id,
                        folder = itemState.folder,
                        name = itemState.name,
                        link = itemState.downloadLink,
                        contentLength = itemState.contentLength,
                        dateAdded = itemState.dateAdded,
                        startTime = itemState.startTime,
                        completeTime = itemState.completeTime,
                        status = when (itemState) {
                            is com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState -> DownloadStatus.Completed
                            is ProcessingDownloadItemState -> when (itemState.status) {
                                is DownloadJobStatus.Downloading -> DownloadStatus.Downloading
                                is DownloadJobStatus.Finished -> DownloadStatus.Completed
                                else -> DownloadStatus.Paused
                            }
                            else -> DownloadStatus.Added
                        }
                    )
                }.filter(selector)
        }
        return downloadListDB.getAll().filter(selector)
    }

    suspend fun getOrCreateDownloadByLink(
        downloadItem: IDownloadItem,
    ): Long {
        val items = getDownloadItemByLink(downloadItem.link)
        if (items.isNotEmpty()) {
            val completedFound = items.find { it.status == DownloadStatus.Completed }
            if (completedFound != null) {
                return completedFound.id
            }
            val id = items.sortedByDescending { it.dateAdded }.first().id
            return id
        }
        val id = addDownload(
            newDownload = NewDownloadItemProps(
                downloadItem = downloadItem,
                onDuplicateStrategy = OnDuplicateStrategy.AddNumbered,
                extraConfig = null,
                context = EmptyContext,
            ),
            queueId = null,
            categoryId = null,
        )
        return id
    }

    fun getDownloadFile(downloadItem: IDownloadItem): File {
        return downloadManager.calculateOutputFile(downloadItem)
    }

    fun getDownloadItemByPath(path: String): IDownloadItemState? {
        return downloadMonitor.downloadListFlow.value.find {
            it.getFullPath().path == path
        }
    }

    fun getDownloadItemsByFolder(folder: String): List<IDownloadItemState> {
        return downloadMonitor.downloadListFlow.value.filter {
            it.folder == folder
        }
    }


    suspend fun getFilePathById(id: Long): File? {
        val item = getDownloadItemById(id) ?: return null
        return downloadManager.calculateOutputFile(item)
    }

    fun addQueue(name: String) {
        scope.launch {
            queueManager.addQueue(name)
        }
    }

    fun getAllDownloadIds(): List<Long> {
        return getUnfinishedDownloadIds() + getFinishedDownloadIds()
    }

    fun getFinishedDownloadIds(): List<Long> {
        return downloadMonitor.completedDownloadListFlow.value.map {
            it.id
        }
    }

    fun getUnfinishedDownloadIds(): List<Long> {
        return downloadMonitor.activeDownloadListFlow.value.map {
            it.id
        }
    }

    fun isDownloadMissingFileOrHaveNotProgress(downloadItem: IDownloadItemState): Boolean {
        val missingFileBypass = if (downloadItem is ProcessingDownloadItemState) {
            // some downloads not started yet so there is no file belong to them, so we shouldn't remove them
            downloadItem.hasProgress
        } else {
            // finished downloads can be removed
            true
        }
        return missingFileBypass && !downloadItem.getFullPath().exists()
    }

    fun getListOfDownloadThatMissingFileOrHaveNotProgress(): List<IDownloadItemState> {
        val downloads = downloadMonitor.downloadListFlow.value
        return downloads.filter {
            isDownloadMissingFileOrHaveNotProgress(it)
        }
    }

    fun getAllRegisteredDownloadFiles(): List<File> {
        return downloadMonitor.run {
            activeDownloadListFlow.value + completedDownloadListFlow.value
        }.map {
            File(it.folder, it.name)
        }
    }

    suspend fun isDownloadActive(id: Long): Boolean {
        return downloadMonitor.isDownloadActiveFlow(id).value
    }

    suspend fun editDownload(
        id: Long,
        applyUpdate: (IDownloadItem) -> Unit,
        downloadJobExtraConfig: DownloadJobExtraConfig?
    ) {
        val wasActive = isDownloadActive(id)
        if (wasActive) {
            manualPause(id)
        }
        downloadManager.updateDownloadItem(
            id = id,
            downloadJobExtraConfig = downloadJobExtraConfig,
            updater = applyUpdate,
        )
        if (wasActive) {
            userManualResume(id)
        }
    }

    suspend fun deleteQueue(queueId: Long) {
        queueManager.deleteQueue(queueId)
        extraQueueSettingsStorage.deleteExtraQueueSettings(queueId)
    }
}

class MockDownloadItem(
    override var id: Long,
    override var folder: String,
    override var name: String,
    override var link: String,
    override var contentLength: Long,
    override var downloadPage: String? = null,
    override var dateAdded: Long = 0,
    override var startTime: Long? = null,
    override var completeTime: Long? = null,
    override var status: com.flowspeed.lib.downloader.downloaditem.DownloadStatus = com.flowspeed.lib.downloader.downloaditem.DownloadStatus.Added,
    override var preferredConnectionCount: Int? = null,
    override var speedLimit: Long = 0,
    override var fileChecksum: String? = null,
) : IDownloadItem {
    override var headers: Map<String, String>? = null
    override fun copy(
        id: arrow.core.Option<Long>,
        folder: arrow.core.Option<String>,
        name: arrow.core.Option<String>,
        link: arrow.core.Option<String>,
        contentLength: arrow.core.Option<Long>,
        downloadPage: arrow.core.Option<String?>,
        dateAdded: arrow.core.Option<Long>,
        startTime: arrow.core.Option<Long?>,
        completeTime: arrow.core.Option<Long?>,
        status: arrow.core.Option<com.flowspeed.lib.downloader.downloaditem.DownloadStatus>,
        preferredConnectionCount: arrow.core.Option<Int?>,
        speedLimit: arrow.core.Option<Long>,
        fileChecksum: arrow.core.Option<String?>,
    ): IDownloadItem = this

    override fun validateItem() {}
    override fun withCredentials(credentials: com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials): IDownloadItem = this
}
