package com.flowspeed.lib.downloader.part

import com.flowspeed.lib.downloader.utils.ExceptionUtils

sealed class PartDownloadStatus {
    interface IsActive
    interface IsInactive


    override fun toString(): String {
        return this::class.simpleName!!
    }

    object IDLE : PartDownloadStatus(),IsInactive
    data class Canceled(val e: Throwable) : PartDownloadStatus(),IsInactive {
        fun isNormalCancellation(): Boolean {
            return ExceptionUtils.isNormalCancellation(e)
        }
    }

    object Completed : PartDownloadStatus(),IsInactive
    object Connecting : PartDownloadStatus(), IsActive
    object ReceivingData : PartDownloadStatus(),IsActive
}
