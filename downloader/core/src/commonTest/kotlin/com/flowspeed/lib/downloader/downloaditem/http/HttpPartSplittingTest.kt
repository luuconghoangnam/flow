package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpPartSplittingTest {

    @Test
    fun `determineParts returns single open part when length is unknown`() {
        val splitter = HttpPartSplitting()
        val parts = splitter.determineParts(
            contentLength = IDownloadItem.LENGTH_UNKNOWN,
            supportsConcurrent = true,
            minPartSize = 1024,
            maxPartCount = 4
        )

        assertEquals(1, parts.size)
        assertEquals(0L, parts[0].from)
        assertNull(parts[0].to)
    }

    @Test
    fun `determineParts returns single part when concurrent is not supported`() {
        val splitter = HttpPartSplitting()
        val parts = splitter.determineParts(
            contentLength = 1000L,
            supportsConcurrent = false,
            minPartSize = 100,
            maxPartCount = 4
        )

        assertEquals(1, parts.size)
        assertEquals(0L, parts[0].from)
        assertEquals(999L, parts[0].to)
    }

    @Test
    fun `determineParts splits parts when concurrent is supported`() {
        val splitter = HttpPartSplitting()
        val parts = splitter.determineParts(
            contentLength = 1000L,
            supportsConcurrent = true,
            minPartSize = 300,
            maxPartCount = 3
        )

        assertEquals(3, parts.size)
        assertEquals(0L, parts[0].from)
        assertEquals(333L, parts[0].to)
        assertEquals(334L, parts[1].from)
        assertEquals(666L, parts[1].to)
        assertEquals(667L, parts[2].from)
        assertEquals(999L, parts[2].to)
    }
}
