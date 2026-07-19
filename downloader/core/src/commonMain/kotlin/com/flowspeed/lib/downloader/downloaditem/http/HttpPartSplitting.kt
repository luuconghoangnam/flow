package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.part.RangedPart
import com.flowspeed.lib.downloader.utils.splitToRange

class HttpPartSplitting {
    fun determineParts(
        contentLength: Long,
        supportsConcurrent: Boolean,
        minPartSize: Long,
        maxPartCount: Long
    ): List<RangedPart> {
        if (contentLength == IDownloadItem.LENGTH_UNKNOWN) {
            return listOf(RangedPart(0, null, 0))
        }
        return if (supportsConcurrent) {
            splitToRange(
                minPartSize = minPartSize,
                maxPartCount = maxPartCount,
                size = contentLength
            ).map {
                RangedPart(it.first, it.last)
            }
        } else {
            listOf(RangedPart(0, (contentLength - 1).takeIf { it >= 0 }, 0))
        }
    }
}
