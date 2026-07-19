package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.exception.DownloadValidationException
import com.flowspeed.lib.downloader.utils.ExceptionUtils

class HttpRetryPolicy(
    private val maxAllowedRetries: Int
) {
    fun shouldPauseImmediately(
        e: Throwable,
        failedDownloadTries: Int,
        isInFirstResume: Boolean,
        shouldRetryIfInitialFailed: Boolean
    ): Boolean {
        if (shouldRetryIfInitialFailed && isInFirstResume && failedDownloadTries == 0) {
            if (ExceptionUtils.isNetworkError(e) || ExceptionUtils.isResponseError(e)) {
                return true
            }
        }
        if (e is DownloadValidationException && e.isCritical()) {
            return true
        }
        return false
    }

    fun getNextRetryState(
        downloadedSize: Long,
        previousDownloadedSizeBeforeRetry: Long,
        failedDownloadTriesBefore: Int
    ): RetryDecision {
        val newFailedTries = if (downloadedSize > previousDownloadedSizeBeforeRetry) {
            0
        } else {
            failedDownloadTriesBefore + 1
        }

        val retriedCount = (newFailedTries - 1).coerceAtLeast(0)
        val shouldRetry = retriedCount < maxAllowedRetries

        return RetryDecision(
            newFailedDownloadTries = newFailedTries,
            newDownloadedSizeBeforeRetry = downloadedSize,
            shouldRetry = shouldRetry
        )
    }
}

data class RetryDecision(
    val newFailedDownloadTries: Int,
    val newDownloadedSizeBeforeRetry: Long,
    val shouldRetry: Boolean
)
