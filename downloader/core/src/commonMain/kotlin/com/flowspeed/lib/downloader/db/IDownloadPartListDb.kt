package com.flowspeed.lib.downloader.db

import com.flowspeed.lib.downloader.part.Parts

interface IDownloadPartListDb {
    suspend fun getParts(id: Long): Parts?
    suspend fun setParts(id: Long, parts: Parts)
    suspend fun clear()
    suspend fun removeParts(id: Long)
}
