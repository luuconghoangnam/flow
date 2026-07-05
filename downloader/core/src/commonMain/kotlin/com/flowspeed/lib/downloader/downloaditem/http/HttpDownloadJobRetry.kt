package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.downloaditem.DownloadJobStatus
import com.flowspeed.lib.downloader.exception.DownloadValidationException
import com.flowspeed.lib.downloader.exception.TooManyErrorException
import com.flowspeed.lib.downloader.utils.ExceptionUtils
import com.flowspeed.lib.util.tryLocked
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Retry policy for [HttpDownloadJob].
 *
 * Handles the decision of whether to retry or give up after a download failure,
 * applying an incremental failure counter and exponential back-off delay.
 */

/**
 * Called when a download fails. Decides whether to retry or call [HttpDownloadJob.pause].
 *
 * Rules:
 * - If first resume and no progress yet → stop (not a transient error).
 * - If a critical [DownloadValidationException] → stop immediately.
 * - If progress was made since the last retry → reset the failure counter.
 * - If retries are exhausted → wrap in [TooManyErrorException] and stop.
 */
internal fun HttpDownloadJob.downloadFailedRetryOrPause(
    e: Throwable,
    isInFirstResume: Boolean,
) {
    scope.launch {
        if (isInFirstResume && failedDownloadTries == 0 && shouldRetryIfInitialFailed()) {
            if (ExceptionUtils.isNetworkError(e) || ExceptionUtils.isResponseError(e)) {
                pause(e)
                return@launch
            }
        }
        if (e is DownloadValidationException && e.isCritical()) {
            pause(e)
            return@launch
        }
        val downloadedSize = getDownloadedSize()
        if (downloadedSize > downloadedSizeBeforeRetry) {
            failedDownloadTries = 0
        } else {
            failedDownloadTries++
        }
        downloadedSizeBeforeRetry = downloadedSize

        val retriedCount = (failedDownloadTries - 1).coerceAtLeast(0)
        if (retriedCount < getMaxAllowedRetries()) {
            retry(isInFirstResume)
        } else {
            pause(TooManyErrorException(e))
        }
    }
}

/**
 * Cancels the current active scope, waits for the retry delay, then resumes download.
 */
internal fun HttpDownloadJob.retry(isInFirstResume: Boolean) {
    scope.launch {
        val newScopeResult = retryLock.tryLocked {
            val job = async {
                saveState()
                cancelDownloadScope()
                stopAllParts()
                _status.update { DownloadJobStatus.Retrying(delayForEachRetry) }
                delay(delayForEachRetry)
                createAndInitializeDownloadScope()
            }
            retryJob = job
            job.await()
        }
        newScopeResult.getOrNull()?.let {
            resumeWithNewScope(it, isInFirstResume)
        }
    }
}
