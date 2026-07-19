package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.exception.DownloadValidationException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpRetryPolicyTest {

    @Test
    fun `shouldPauseImmediately returns true for critical download validation exception`() {
        val policy = HttpRetryPolicy(maxAllowedRetries = 3)
        val criticalEx = object : DownloadValidationException("Critical failure") {
            override fun isCritical(): Boolean = true
        }

        assertTrue(policy.shouldPauseImmediately(criticalEx, failedDownloadTries = 0, isInFirstResume = true, shouldRetryIfInitialFailed = true))
        assertTrue(policy.shouldPauseImmediately(criticalEx, failedDownloadTries = 1, isInFirstResume = false, shouldRetryIfInitialFailed = false))
    }

    @Test
    fun `shouldPauseImmediately returns false for non-critical download validation exception`() {
        val policy = HttpRetryPolicy(maxAllowedRetries = 3)
        val nonCriticalEx = object : DownloadValidationException("Non-critical failure") {
            override fun isCritical(): Boolean = false
        }

        assertFalse(policy.shouldPauseImmediately(nonCriticalEx, failedDownloadTries = 0, isInFirstResume = true, shouldRetryIfInitialFailed = true))
    }

    @Test
    fun `shouldPauseImmediately returns true on network or response error in first resume when tries is zero and retry flag is true`() {
        val policy = HttpRetryPolicy(maxAllowedRetries = 3)
        val netEx = java.net.SocketException("Network down")

        assertTrue(policy.shouldPauseImmediately(netEx, failedDownloadTries = 0, isInFirstResume = true, shouldRetryIfInitialFailed = true))
        assertFalse(policy.shouldPauseImmediately(netEx, failedDownloadTries = 1, isInFirstResume = true, shouldRetryIfInitialFailed = true))
        assertFalse(policy.shouldPauseImmediately(netEx, failedDownloadTries = 0, isInFirstResume = false, shouldRetryIfInitialFailed = true))
        assertFalse(policy.shouldPauseImmediately(netEx, failedDownloadTries = 0, isInFirstResume = true, shouldRetryIfInitialFailed = false))
    }

    @Test
    fun `getNextRetryState increments tries when no progress and determines if should retry`() {
        val policy = HttpRetryPolicy(maxAllowedRetries = 2)

        // Tries = 0, no progress (from 0 to 0) -> tries = 1, retriedCount = 0 < 2 -> shouldRetry = true
        val state1 = policy.getNextRetryState(downloadedSize = 0, previousDownloadedSizeBeforeRetry = 0, failedDownloadTriesBefore = 0)
        assertEquals(1, state1.newFailedDownloadTries)
        assertEquals(0, state1.newDownloadedSizeBeforeRetry)
        assertTrue(state1.shouldRetry)

        // Tries = 1, no progress -> tries = 2, retriedCount = 1 < 2 -> shouldRetry = true
        val state2 = policy.getNextRetryState(downloadedSize = 0, previousDownloadedSizeBeforeRetry = 0, failedDownloadTriesBefore = 1)
        assertEquals(2, state2.newFailedDownloadTries)
        assertTrue(state2.shouldRetry)

        // Tries = 2, no progress -> tries = 3, retriedCount = 2 not < 2 -> shouldRetry = false
        val state3 = policy.getNextRetryState(downloadedSize = 0, previousDownloadedSizeBeforeRetry = 0, failedDownloadTriesBefore = 2)
        assertEquals(3, state3.newFailedDownloadTries)
        assertFalse(state3.shouldRetry)
    }

    @Test
    fun `getNextRetryState resets tries when progress is detected`() {
        val policy = HttpRetryPolicy(maxAllowedRetries = 2)

        // Progress detected (100 > 50) -> tries reset to 0, retriedCount = 0 < 2 -> shouldRetry = true
        val state = policy.getNextRetryState(downloadedSize = 100, previousDownloadedSizeBeforeRetry = 50, failedDownloadTriesBefore = 3)
        assertEquals(0, state.newFailedDownloadTries)
        assertEquals(100, state.newDownloadedSizeBeforeRetry)
        assertTrue(state.shouldRetry)
    }
}
