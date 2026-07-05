package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.connection.response.expectSuccess
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.exception.FileChangedException
import com.flowspeed.lib.downloader.exception.ServerResumeSupportChangeException
import com.flowspeed.lib.downloader.utils.TimeUtils

/**
 * HTTP response validation for [HttpDownloadJob].
 *
 * Handles the HEAD request phase: checks ETag, content-length changes,
 * resume support changes, and webpage detection.
 */

/**
 * Sends a test request to the server and validates the response against
 * the stored download item state.
 *
 * Updates [HttpDownloadJob.supportsConcurrent], [HttpDownloadJob.serverLastModified],
 * and download item metadata. Throws on irrecoverable changes.
 *
 * @throws ServerResumeSupportChangeException if the server previously supported resume but no longer does.
 * @throws FileChangedException if content-length or ETag indicates the remote file changed.
 * @throws FileChangedException.GotAWebPage if a non-webpage download now returns HTML.
 */
internal suspend fun HttpDownloadJob.fetchDownloadInfoAndValidate() {
    val response = client.test(downloadItem).expectSuccess()

    // Detect if server dropped resume support after parts were already created
    supportsConcurrent?.let { previouslyConcurrent ->
        if (previouslyConcurrent && !response.resumeSupport) {
            throw ServerResumeSupportChangeException()
        }
    }

    supportsConcurrent = response.resumeSupport
    serverLastModified = runCatching {
        response.lastModified?.let(TimeUtils::convertLastModifiedHeaderToTimestamp)
    }.getOrNull()

    if (response.isWebPage) {
        handleWebPageResponse()
    } else {
        validateContentLengthAndETag(response.totalLength, response.etag)
    }

    saveState()
}

private fun HttpDownloadJob.handleWebPageResponse() {
    if (isDownloadItemIsAWebpage()) {
        // Webpage being downloaded as-is: disable strict mode and multi-connection
        strictDownload = false
        supportsConcurrent = false
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.serverETag = null
    } else {
        // A non-webpage download suddenly returns HTML → link changed or expired
        throw FileChangedException.GotAWebPage()
    }
}

private fun HttpDownloadJob.validateContentLengthAndETag(
    totalLength: Long?,
    newServerETag: String?,
) {
    val oldServerETag = downloadItem.serverETag

    if (downloadItem.contentLength == IDownloadItem.LENGTH_UNKNOWN) {
        // Fresh download or reset — accept whatever the server reports
        downloadItem.contentLength = totalLength ?: -1
        downloadItem.serverETag = newServerETag
    } else {
        // Resuming — verify the file hasn't changed on the server
        if (totalLength != downloadItem.contentLength) {
            throw FileChangedException.LengthChangedException(
                downloadItem.contentLength,
                totalLength ?: -1
            )
        }
        if (oldServerETag != null && newServerETag != null && oldServerETag != newServerETag) {
            throw FileChangedException.ETagChangedException(oldServerETag, newServerETag)
        }
    }
}

internal fun HttpDownloadJob.isDownloadItemIsAWebpage(): Boolean {
    return downloadItem.name.endsWith(".html", true)
}
