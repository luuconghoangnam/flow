package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.DownloadSettings
import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.connection.Connection
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.db.MemoryDownloadListDB
import com.flowspeed.lib.downloader.db.MemoryDownloadPartStatesDB
import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.part.PartDownloadStatus
import com.flowspeed.lib.downloader.part.RangedPart
import com.flowspeed.lib.downloader.part.RangedParts
import com.flowspeed.lib.downloader.utils.EmptyFileCreator
import com.flowspeed.lib.downloader.utils.IDiskStat
import kotlinx.coroutines.test.runTest
import okio.Buffer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpDownloadJobStateTest {

    private lateinit var dir: File

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("http-job-state-test").toFile()
    }

    @AfterTest
    fun teardown() {
        dir.deleteRecursively()
    }

    @Test
    fun `boot restores downloaded size from persisted parts`() = runTest {
        val partDb = MemoryDownloadPartStatesDB()
        partDb.setParts(
            1,
            RangedParts(
                listOf(
                    RangedPart(from = 0, to = 99, current = 40),
                    RangedPart(from = 100, to = 199, current = 160),
                )
            )
        )
        val job = newJob(partDb = partDb)

        try {
            job.boot()

            assertEquals(100, job.getDownloadedSize())
            assertEquals(2, job.getParts().size)
        } finally {
            job.close()
        }
    }

    @Test
    fun `boot marks persisted completed parts completed`() = runTest {
        val partDb = MemoryDownloadPartStatesDB()
        partDb.setParts(
            1,
            RangedParts(listOf(RangedPart(from = 0, to = 99, current = 100)))
        )
        val job = newJob(partDb = partDb)

        try {
            job.boot()

            assertEquals(PartDownloadStatus.Completed, job.getParts().single().statusFlow.value)
        } finally {
            job.close()
        }
    }

    @Test
    fun `reset clears resumable state and persists empty parts`() = runTest {
        val partDb = MemoryDownloadPartStatesDB()
        partDb.setParts(
            1,
            RangedParts(listOf(RangedPart(from = 0, to = 99, current = 40)))
        )
        val item = newItem().apply {
            contentLength = 100
            serverETag = "old-etag"
            status = DownloadStatus.Error
            startTime = 10
            completeTime = 20
        }
        val job = newJob(item = item, partDb = partDb)

        try {
            job.boot()
            job.reset()

            assertEquals(IDownloadItem.LENGTH_UNKNOWN, item.contentLength)
            assertNull(item.serverETag)
            assertEquals(DownloadStatus.Added, item.status)
            assertNull(item.startTime)
            assertNull(item.completeTime)
            assertTrue(job.getParts().isEmpty())
            assertTrue((partDb.getParts(item.id) as RangedParts).list.isEmpty())
        } finally {
            job.close()
        }
    }

    private fun newJob(
        item: HttpDownloadItem = newItem(),
        partDb: MemoryDownloadPartStatesDB = MemoryDownloadPartStatesDB(),
    ): HttpDownloadJob {
        val manager = DownloadManager(
            dlListDb = MemoryDownloadListDB(),
            partListDb = partDb,
            settings = DownloadSettings(),
            emptyFileCreator = EmptyFileCreator(
                diskStat = object : IDiskStat {
                    override fun getRemainingSpace(path: File): Long = Long.MAX_VALUE
                },
                useSparseFile = { false },
            ),
            downloaderRegistry = DownloaderRegistry(),
            downloadDataFolder = dir,
        )
        return HttpDownloadJob(
            downloadItem = item,
            downloadManager = manager,
            client = FakeHttpDownloaderClient(),
        )
    }

    private fun newItem() = HttpDownloadItem(
        link = "https://example.com/file.bin",
        id = 1,
        folder = dir.absolutePath,
        name = "file.bin",
    )

    private class FakeHttpDownloaderClient : HttpDownloaderClient() {
        override suspend fun actualHead(
            credentials: IHttpDownloadCredentials,
            start: Long?,
            end: Long?,
        ): HttpResponseInfo = responseInfo()

        override suspend fun actualConnect(
            credentials: IHttpBasedDownloadCredentials,
            start: Long?,
            end: Long?,
        ): Connection<HttpResponseInfo> = Connection(
            source = Buffer(),
            contentLength = 0,
            responseInfo = responseInfo(),
        )

        private fun responseInfo() = HttpResponseInfo(
            statusCode = 200,
            message = "OK",
            requestUrl = "https://example.com/file.bin",
            responseHeaders = mapOf("content-length" to "0"),
        )
    }
}
