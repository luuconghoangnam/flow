package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.exception.DownloadValidationException
import com.flowspeed.lib.downloader.part.PartDownloadStatus
import com.flowspeed.lib.downloader.part.RangedPart
import com.flowspeed.lib.downloader.part.RangedParts
import com.flowspeed.lib.downloader.utils.splitToRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Part management for [HttpDownloadJob].
 *
 * Handles creating, loading, splitting, and driving part downloaders.
 */

// ---------------------------------------------------------------------------
// Part state — load & create
// ---------------------------------------------------------------------------

internal suspend fun HttpDownloadJob.loadPartState() {
    val rangedParts = partLock.withLock {
        partListDb.getParts(id)
    } as? RangedParts
    setParts(rangedParts?.list.orEmpty())
}

internal suspend fun HttpDownloadJob.createPartsIfNotCreated() {
    if (parts.isNotEmpty()) return

    if (downloadItem.contentLength == IDownloadItem.LENGTH_UNKNOWN) {
        setParts(listOf(RangedPart(0, null, 0)))
    } else {
        if (supportsConcurrent == true) {
            setParts(
                splitToRange(
                    minPartSize = downloadManager.settings.minPartSize,
                    maxPartCount = getRequestedPartitionCount().toLong(),
                    size = downloadItem.contentLength,
                ).map { RangedPart(it.first, it.last) }
            )
        } else {
            setParts(listOf(RangedPart(0, (downloadItem.contentLength - 1).takeIf { it >= 0 }, 0)))
        }
    }
    saveState()
}

// ---------------------------------------------------------------------------
// Part downloader lifecycle — begin, stop, create, destroy
// ---------------------------------------------------------------------------

internal fun HttpDownloadJob.beginDownloadParts() {
    if (partLoopLock.isLocked) return
    activeDownloadScope?.launch {
        if (!partLoopLock.tryLock()) return@launch
        try {
            val activeCount = getPartDownloaderList().count { it.active }
            val howMuchCreate = getRequestedPartitionCount() - activeCount

            if (howMuchCreate > 0) {
                val inactive = getPartDownloaderList()
                    .filter { !it.active && !it.part.isCompleted }
                    .sortedBy { it.part.from }
                    .toMutableList()

                fun nextPartDownloader(): HttpPartDownloader? {
                    runCatching { inactive.removeAt(0) }.getOrNull()?.let { return it }
                    if (supportsConcurrent == true && downloadManager.settings.dynamicPartCreationMode) {
                        synchronized(partSplitLock) {
                            val candidates = getPartDownloaderList().toList()
                                .filter { it.canBeSplit() }
                                .sortedByDescending { it.part.remainingLength }
                            for (candidate in candidates) {
                                val newPart = candidate.splitPart()
                                if (newPart != null) {
                                    parts.add(newPart)
                                    parts.sortBy { it.from }
                                    return getOrCreatePartDownloader(newPart)
                                }
                            }
                        }
                    }
                    return null
                }

                repeat(howMuchCreate) {
                    val pd = nextPartDownloader() ?: return@repeat
                    if (!pd.part.isCompleted) pd.start()
                }
            }

            if (howMuchCreate < 0) {
                getPartDownloaderList().filter { it.active }
                    .sortedByDescending { it.part.from }
                    .take(-howMuchCreate)
                    .onEach { it.stop() }
                    .onEach { it.join(); it.awaitIdle() }
            }
        } finally {
            partLoopLock.unlock()
        }
    }
}

internal fun HttpDownloadJob.createPartDownloaderList() {
    synchronized(partDownloaderList) {
        parts.forEach { getOrCreatePartDownloader(it) }
    }
}

internal fun HttpDownloadJob.clearPartDownloaderList() {
    parts.forEach { destroyPartDownloader(it) }
}

internal fun HttpDownloadJob.getPartDownloaderList(): List<HttpPartDownloader> {
    synchronized(partDownloaderList) {
        return partDownloaderList.map { it.value }
    }
}

internal fun HttpDownloadJob.getOrCreatePartDownloader(part: RangedPart): HttpPartDownloader {
    synchronized(partDownloaderList) {
        return partDownloaderList.getOrPut(part.from) {
            HttpPartDownloader(
                credentials = downloadItem,
                part = part,
                getDestWriter = { destination.getWriterFor(part) },
                client = client,
                speedLimiters = listOf(downloadManager.throttler, jobThrottler),
                strictMode = strictDownload,
                partSplitLock = partSplitLock
            ).also { pd ->
                pd.onTooManyErrors = { onPartHaveToManyError(it) }
                listenerJobs[part.from] = pd.statusFlow.onEach { status ->
                    onPartStatusChanged(pd, status)
                }.launchIn(scope)
            }
        }
    }
}

internal fun HttpDownloadJob.destroyPartDownloader(part: RangedPart) {
    listenerJobs.remove(part.from)?.cancel()
    partDownloaderList.remove(part.from)
}

internal suspend fun HttpDownloadJob.stopAllParts() {
    withContext(Dispatchers.IO) {
        partDownloaderList.values.onEach { it.stop() }.onEach { it.join(); it.awaitIdle() }
    }
}

// ---------------------------------------------------------------------------
// Part event handlers
// ---------------------------------------------------------------------------

internal fun HttpDownloadJob.onPartStatusChanged(
    partDownloader: HttpPartDownloader,
    partStatus: PartDownloadStatus,
) {
    when (partStatus) {
        is PartDownloadStatus.Canceled -> destination.onPartCancelled(partDownloader.part)
        PartDownloadStatus.Completed -> {
            destination.onPartCancelled(partDownloader.part)
            if (getParts().all { it.isCompleted }) {
                onDownloadFinished()
            } else {
                scope.launch { beginDownloadParts() }
            }
        }
        PartDownloadStatus.ReceivingData, PartDownloadStatus.Connecting, PartDownloadStatus.IDLE -> {}
    }
}

internal fun HttpDownloadJob.onPartHaveToManyError(throwable: Throwable) {
    var paused = false
    if (throwable is DownloadValidationException && throwable.isCritical()) {
        paused = true
        scope.launch { pause(throwable) }
    }
    val allHaveError = getPartDownloaderList().filter { it.active }.all { it.injured() }
    if (allHaveError && !paused) {
        downloadFailedRetryOrPause(e = throwable, isInFirstResume = false)
    }
}
