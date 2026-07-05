package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.destination.DownloadDestination
import com.flowspeed.lib.downloader.destination.SimpleDownloadDestination
import com.flowspeed.lib.downloader.downloaditem.DownloadJob
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.DownloadJobStatus
import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.exception.PrepareDestinationFailedException
import com.flowspeed.lib.downloader.exception.ServerResumeSupportChangeException
import com.flowspeed.lib.downloader.part.RangedPart
import com.flowspeed.lib.downloader.utils.ExceptionUtils
import com.flowspeed.lib.downloader.utils.printStackIfNOtUsual
import com.flowspeed.lib.downloader.utils.throwIfCancelled
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Throttler
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the full lifecycle of a single HTTP (or ranged) download.
 *
 * Heavy responsibilities are split into focused extension files:
 *  - [HttpDownloadJobFetch]  — HEAD request validation (ETag, length, webpage detection)
 *  - [HttpDownloadJobParts]  — part creation, splitting, and downloader lifecycle
 *  - [HttpDownloadJobRetry]  — retry policy and failure counting
 *
 * This file owns: field declarations, lifecycle entry-points (resume / pause / reset),
 * destination management, config changes, and state persistence.
 */
class HttpDownloadJob(
    override val downloadItem: HttpDownloadItem,
    downloadManager: DownloadManager,
    val client: HttpDownloaderClient,
) : DownloadJob(downloadManager = downloadManager) {

    val listDb by downloadManager::dlListDb
    val partListDb by downloadManager::partListDb

    internal val parts: MutableList<RangedPart> = mutableListOf()
    internal lateinit var destination: SimpleDownloadDestination

    override fun getDestination(): DownloadDestination = destination

    var supportsConcurrent: Boolean? = null
        internal set

    var serverLastModified: Long? = null
        internal set

    // if strictDownload is false, part downloader downloads without content-length validation.
    // Only acceptable when resume is not supported.
    @Volatile
    internal var strictDownload = true

    // ---------------------------------------------------------------------------
    // Retry state (used by HttpDownloadJobRetry extensions)
    // ---------------------------------------------------------------------------

    var _maxAllowedRetries: Int? = null
    fun getMaxAllowedRetries(): Int = _maxAllowedRetries ?: downloadManager.settings.maxDownloadRetryCount

    var failedDownloadTries = 0
    val delayForEachRetry = 3_000L
    var downloadedSizeBeforeRetry = 0L
    var retryJob: Job? = null
    val retryLock = Mutex()

    // ---------------------------------------------------------------------------
    // Part downloader map (used by HttpDownloadJobParts extensions)
    // ---------------------------------------------------------------------------

    internal val jobThrottler = Throttler()
    internal val partDownloaderList = ConcurrentHashMap<Long, HttpPartDownloader>()
    internal val listenerJobs: MutableMap<Long, Job> = ConcurrentHashMap<Long, Job>()
    internal val partSplitLock = Any()
    internal val partLoopLock = Mutex()

    // ---------------------------------------------------------------------------
    // Persistence locks
    // ---------------------------------------------------------------------------

    val itemSaveLock = Mutex()
    val partLock = Mutex()

    // ---------------------------------------------------------------------------
    // Boot
    // ---------------------------------------------------------------------------

    override suspend fun actualBoot() {
        initializeDestination()
        loadPartState()
        supportsConcurrent = when (getParts().size) {
            in 2..Int.MAX_VALUE -> true
            else -> null
        }
        applySpeedLimit()
        downloadedSizeBeforeRetry = getDownloadedSize()
    }

    override fun initializeDestination() {
        val outFile = downloadManager.calculateOutputFile(downloadItem)
        destination = SimpleDownloadDestination(
            file = outFile,
            emptyFileCreator = downloadManager.emptyFileCreator,
            appendExtensionToIncompleteDownloads = downloadManager.settings.appendExtensionToIncompleteDownloads,
            downloadId = id
        )
    }

    // ---------------------------------------------------------------------------
    // Part helpers (accessed by extension files)
    // ---------------------------------------------------------------------------

    internal fun setParts(list: List<RangedPart>) {
        parts.clear()
        list.forEach { if (it.isCompleted) it.statusFlow.update { com.flowspeed.lib.downloader.part.PartDownloadStatus.Completed } }
        parts.addAll(list)
    }

    fun getParts(): List<RangedPart> = parts.toList()

    fun getRequestedPartitionCount(): Int =
        downloadItem.preferredConnectionCount ?: downloadManager.settings.defaultThreadCount

    private fun applySpeedLimit() {
        jobThrottler.bytesPerSecond(bytesPerSecond = downloadItem.speedLimit)
    }

    override fun getDownloadedSize(): Long = getParts().sumOf { it.howMuchProceed() }

    // ---------------------------------------------------------------------------
    // Resume
    // ---------------------------------------------------------------------------

    override suspend fun resume() {
        if (isDownloadActive.value) return
        _isDownloadActive.update { true }
        resumeWithNewScope(
            newActiveScope = createAndInitializeDownloadScope(),
            isInFirstResume = true
        )
    }

    fun createAndInitializeDownloadScope(): CoroutineScope =
        newScopeBasedOn(scope).also { activeDownloadScope = it }

    internal suspend fun resumeWithNewScope(
        newActiveScope: CoroutineScope,
        isInFirstResume: Boolean,
    ) = newActiveScope.launch {
        boot()
        if (parts.isNotEmpty() && parts.all { it.isCompleted }) {
            onDownloadFinished()
            return@launch
        }
        onDownloadResuming()
        try {
            fetchDownloadInfoAndValidate()
            createPartsIfNotCreated()
            prepareDestination { _status.value = DownloadJobStatus.PreparingFile(it) }
            createPartDownloaderList()
            beginDownloadParts()
            startAutoSaver()
            downloadItem.status = DownloadStatus.Downloading
            if (downloadItem.startTime == null) downloadItem.startTime = System.currentTimeMillis()
            saveState()
            onDownloadResumed()
        } catch (e: Exception) {
            e.printStackIfNOtUsual()
            val shouldStop = when {
                ExceptionUtils.isNormalCancellation(e) -> true
                e is com.flowspeed.lib.downloader.exception.DownloadValidationException -> e.isCritical()
                else -> false
            }
            if (shouldStop) {
                scope.launch { pause(e) }
            } else {
                downloadFailedRetryOrPause(e = e, isInFirstResume = isInFirstResume)
            }
        }
    }.join()

    // ---------------------------------------------------------------------------
    // Pause / cancel
    // ---------------------------------------------------------------------------

    override suspend fun pause(throwable: Throwable) {
        boot()
        failedDownloadTries = 0
        cancelRetry()
        cancelDownloadScope()
        stopAllParts()
        clearPartDownloaderList()
        onDownloadCanceled(throwable)
    }

    suspend fun cancelDownloadScope() {
        activeDownloadScope?.coroutineContext?.job?.cancelAndJoin()
        activeDownloadScope = null
    }

    suspend fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    // ---------------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------------

    override suspend fun reset() {
        pause()
        clearPartDownloaderList()
        setParts(emptyList())
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.serverETag = null
        downloadItem.status = DownloadStatus.Added
        downloadItem.startTime = null
        downloadItem.completeTime = null
        strictDownload = true
        downloadedSizeBeforeRetry = 0
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
    }

    // ---------------------------------------------------------------------------
    // Config changes
    // ---------------------------------------------------------------------------

    override suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?,
    ): IDownloadItem {
        boot()
        val previousItem = downloadItem.copy()
        val newItem = previousItem.copy().apply(updater)
        val previousDestination = downloadManager.calculateOutputFile(previousItem)
        val newDestination = downloadManager.calculateOutputFile(newItem)
        val shouldUpdateDestination = previousDestination != newDestination
        if (shouldUpdateDestination) {
            if (isDownloadActive.value) pause()
            destination.moveOutput(newDestination)
        }
        downloadItem.applyFrom(newItem)
        if (shouldUpdateDestination) initializeDestination()
        if (previousItem.preferredConnectionCount != downloadItem.preferredConnectionCount) {
            onPreferredConnectionCountChanged()
        }
        if (previousItem.link != downloadItem.link) onLinkChanged()
        applySpeedLimit()
        extraConfig?.let { extraConfigsReceived(it) }
        saveDownloadItem()
        return downloadItem
    }

    fun onPreferredConnectionCountChanged() {
        activeDownloadScope?.launch { beginDownloadParts() }
    }

    fun onLinkChanged() {
        scope.launch {
            if (activeDownloadScope?.isActive == true) {
                pause()
                resume()
            }
        }
    }

    override fun reloadSettings() {
        onPreferredConnectionCountChanged()
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) {
        // no extra configs
    }

    // ---------------------------------------------------------------------------
    // Destination helpers
    // ---------------------------------------------------------------------------

    private suspend fun prepareDestination(onProgressUpdate: (Int?) -> Unit) {
        withContext(Dispatchers.IO) {
            destination.outputSize = downloadItem.contentLength
                .takeIf { strictDownload }
                ?.takeIf { supportsConcurrent != false }
                ?: IDownloadItem.LENGTH_UNKNOWN
            try {
                destination.prepareDestinationFolder()
            } catch (e: Exception) {
                e.throwIfCancelled()
                throw PrepareDestinationFailedException(e)
            }
            if (!destination.isDownloadedPartsIsValid()) {
                parts.forEach { it.resetCurrent() }
                saveState()
            }
            try {
                destination.prepareFile(onProgressUpdate)
            } catch (e: Exception) {
                e.throwIfCancelled()
                throw PrepareDestinationFailedException(e)
            }
            val lastModified = serverLastModified.takeIf { downloadManager.settings.useServerLastModifiedTime }
            destination.setLastModified(lastModified)
        }
    }

    // ---------------------------------------------------------------------------
    // State persistence
    // ---------------------------------------------------------------------------

    private var lastSavedDownloadItem: HttpDownloadItem? = null
    private var lastSavedParts: List<RangedPart>? = null

    suspend fun saveDownloadItem() {
        itemSaveLock.withLock {
            val copy = downloadItem.copy()
            if (lastSavedDownloadItem != downloadItem) {
                listDb.update(downloadItem)
                lastSavedDownloadItem = copy
            }
        }
    }

    private suspend fun saveParts() {
        partLock.withLock {
            val copy = getParts().map { it.copy() }
            if (lastSavedParts != copy) {
                destination.flush()
                partListDb.setParts(id, com.flowspeed.lib.downloader.part.RangedParts(copy))
                lastSavedParts = copy
            }
        }
    }

    override suspend fun saveState() {
        saveDownloadItem()
        saveParts()
    }

    override fun onDownloadFinishedBeforeSave() {
        if (downloadItem.contentLength == IDownloadItem.LENGTH_UNKNOWN && parts.size == 1) {
            downloadItem.contentLength = parts[0].howMuchProceed()
        }
    }

    // ---------------------------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------------------------

    fun expectValid(size: Long, parts: List<LongRange>) {
        val sorted = parts.sortedBy { it.first }
        require(sorted.first().first == 0L)
        require(sorted.last().last == size - 1)
        for (i in 1..<sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            require(a.last + 1 == b.first)
        }
    }

    fun shouldRetryIfInitialFailed(): Boolean = true
}
