package com.flowspeed.link.desktop.ipc

import com.flowspeed.link.service.ipc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Proxy for DownloadSystem that communicates with the background service via IPC.
 *
 * The UI process uses this instead of a local DownloadSystem when running
 * in 2-process mode. All operations are forwarded to the service.
 *
 * Provides StateFlows for download list and progress that are populated
 * by polling the service (will be replaced with SSE in future).
 */
import com.flowspeed.link.shared.util.IRemoteDownloadDelegate
import com.flowspeed.lib.downloader.NewDownloadItemProps
import com.flowspeed.link.shared.util.category.CategorySelectionMode

class RemoteDownloadSystem(
    private val ipcClient: IpcClient,
    private val scope: CoroutineScope,
) : IRemoteDownloadDelegate {
    private val _downloads = MutableStateFlow<List<IpcDownloadItem>>(emptyList())
    val downloads: StateFlow<List<IpcDownloadItem>> = _downloads.asStateFlow()

    private val _activeDownloadCount = MutableStateFlow(0)
    val activeDownloadCount: StateFlow<Int> = _activeDownloadCount.asStateFlow()

    private val _queues = MutableStateFlow<List<IpcQueueItem>>(emptyList())
    val queues: StateFlow<List<IpcQueueItem>> = _queues.asStateFlow()

    /** Start polling the service for state updates. */
    fun startPolling() {
        // Poll downloads every 1 second when connected
        scope.launch {
            while (true) {
                if (ipcClient.connectionState.value == IpcClient.ConnectionState.Connected) {
                    refreshDownloads()
                    refreshQueues()
                }
                delay(1000)
            }
        }
    }

    suspend fun refreshDownloads() {
        val response = ipcClient.getDownloads()
        if (response != null) {
            _downloads.value = response.downloads
            _activeDownloadCount.value = response.downloads.count {
                it.status == "Downloading"
            }
        }
    }

    suspend fun refreshQueues() {
        val response = ipcClient.getQueues()
        if (response != null) {
            _queues.value = response.queues
        }
    }

    // --- Download operations ---

    override suspend fun addDownload(
        newItemsToAdd: List<NewDownloadItemProps>,
        queueId: Long?,
        categorySelectionMode: CategorySelectionMode?,
    ): List<Long> {
        val request = IpcAddDownloadRequest(
            items = newItemsToAdd.map { item ->
                IpcNewDownloadItem(
                    link = item.downloadItem.link,
                    headers = item.downloadItem.headers,
                    name = item.downloadItem.name,
                    folder = item.downloadItem.folder,
                    downloadPage = item.downloadItem.downloadPage,
                )
            },
            queueId = queueId,
        )
        val response = ipcClient.addDownload(request)
        return response?.ids ?: emptyList()
    }

    suspend fun addDownload(
        link: String,
        headers: Map<String, String>? = null,
        name: String? = null,
        folder: String? = null,
        queueId: Long? = null,
    ): List<Long> {
        val request = IpcAddDownloadRequest(
            items = listOf(
                IpcNewDownloadItem(
                    link = link,
                    headers = headers,
                    name = name,
                    folder = folder,
                )
            ),
            queueId = queueId,
        )
        val response = ipcClient.addDownload(request)
        return response?.ids ?: emptyList()
    }

    override suspend fun pauseDownload(id: Long): Boolean {
        return ipcClient.pauseDownload(id)?.ok == true
    }

    override suspend fun resumeDownload(id: Long): Boolean {
        return ipcClient.resumeDownload(id)?.ok == true
    }

    override suspend fun removeDownload(id: Long, alsoRemoveFile: Boolean) {
        ipcClient.deleteDownload(id)
    }

    suspend fun deleteDownload(id: Long): Boolean {
        return ipcClient.deleteDownload(id)?.ok == true
    }

    override suspend fun resetDownload(id: Long): Boolean {
        return ipcClient.resetDownload(id)?.ok == true
    }

    // --- Queue operations ---

    suspend fun createQueue(name: String): Boolean {
        return ipcClient.createQueue(name)?.ok == true
    }

    override suspend fun startQueue(queueId: Long) {
        ipcClient.startQueue(queueId)
    }

    suspend fun startQueueOld(id: Long): Boolean {
        return ipcClient.startQueue(id)?.ok == true
    }

    override suspend fun stopQueue(queueId: Long) {
        ipcClient.stopQueue(queueId)
    }

    suspend fun stopQueueOld(id: Long): Boolean {
        return ipcClient.stopQueue(id)?.ok == true
    }

    suspend fun deleteQueue(id: Long): Boolean {
        return ipcClient.deleteQueue(id)?.ok == true
    }

    // --- Config ---

    suspend fun getConfig(): IpcConfig? {
        return ipcClient.getConfig()
    }

    suspend fun updateConfig(config: IpcConfig): Boolean {
        return ipcClient.updateConfig(config)?.ok == true
    }

    // --- Lifecycle ---

    suspend fun shutdownService(): Boolean {
        return ipcClient.shutdown()?.ok == true
    }
}
