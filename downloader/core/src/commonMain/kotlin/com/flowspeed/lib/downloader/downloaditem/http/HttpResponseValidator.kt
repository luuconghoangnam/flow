package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.exception.FileChangedException
import com.flowspeed.lib.downloader.exception.ServerResumeSupportChangeException
import com.flowspeed.lib.downloader.utils.TimeUtils

class HttpResponseValidator {
    data class ValidationResult(
        val supportsConcurrent: Boolean,
        val serverLastModified: Long?,
        val strictDownload: Boolean,
        val newContentLength: Long,
        val newServerETag: String?
    )

    fun validate(
        response: HttpResponseInfo,
        downloadItem: HttpDownloadItem,
        previouslySupportsConcurrent: Boolean?,
        isWebpageName: Boolean
    ): ValidationResult {
        previouslySupportsConcurrent?.let { previouslyConcurrentWasSupported ->
            if (previouslyConcurrentWasSupported && !response.resumeSupport) {
                throw ServerResumeSupportChangeException()
            }
        }

        var nextSupportsConcurrent = response.resumeSupport
        val nextServerLastModified = runCatching {
            response.lastModified?.let(TimeUtils::convertLastModifiedHeaderToTimestamp)
        }.getOrNull()

        var nextStrictDownload = true
        var nextContentLength = downloadItem.contentLength
        var nextServerETag = downloadItem.serverETag

        if (response.isWebPage) {
            if (isWebpageName) {
                nextStrictDownload = false
                nextSupportsConcurrent = false
                nextContentLength = IDownloadItem.LENGTH_UNKNOWN
                nextServerETag = null
            } else {
                throw FileChangedException.GotAWebPage()
            }
        }

        val totalLength = response.totalLength
        val newServerETag = response.etag

        if (nextContentLength == IDownloadItem.LENGTH_UNKNOWN) {
            nextContentLength = totalLength ?: -1
            nextServerETag = newServerETag
        } else {
            if (totalLength != nextContentLength) {
                throw FileChangedException.LengthChangedException(nextContentLength, totalLength ?: -1)
            }
            if (nextServerETag != null && newServerETag != null) {
                if (nextServerETag != newServerETag) {
                    throw FileChangedException.ETagChangedException(nextServerETag!!, newServerETag)
                }
            }
        }

        return ValidationResult(
            supportsConcurrent = nextSupportsConcurrent,
            serverLastModified = nextServerLastModified,
            strictDownload = nextStrictDownload,
            newContentLength = nextContentLength,
            newServerETag = nextServerETag
        )
    }
}
