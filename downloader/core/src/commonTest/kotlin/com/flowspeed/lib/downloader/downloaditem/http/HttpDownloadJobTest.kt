package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.DownloadSettings
import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.connection.Connection
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.db.MemoryDownloadListDB
import com.flowspeed.lib.downloader.db.MemoryDownloadPartStatesDB
import com.flowspeed.lib.downloader.utils.EmptyFileCreator
import com.flowspeed.lib.downloader.utils.IDiskStat
import okio.Buffer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpDownloadJobTest {

    private lateinit var dir: File

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("http-job-test").toFile()
    }

    @AfterTest
    fun teardown() {
        dir.deleteRecursively()
    }

    @Test
    fun `expectValid accepts contiguous ranges covering full file`() {
        val job = newJob()

        job.expectValid(
            size = 100,
            parts = listOf(0L..24L, 25L..49L, 50L..99L),
        )
    }

    @Test
    fun `expectValid rejects ranges that do not start at zero`() {
        val job = newJob()

        assertFailsWith<IllegalArgumentException> {
            job.expectValid(size = 100, parts = listOf(1L..99L))
        }
    }

    @Test
    fun `expectValid rejects ranges that do not end at size minus one`() {
        val job = newJob()

        assertFailsWith<IllegalArgumentException> {
            job.expectValid(size = 100, parts = listOf(0L..98L))
        }
    }

    @Test
    fun `expectValid rejects gaps between ranges`() {
        val job = newJob()

        assertFailsWith<IllegalArgumentException> {
            job.expectValid(size = 100, parts = listOf(0L..49L, 51L..99L))
        }
    }

    @Test
    fun `expectValid rejects overlaps between ranges`() {
        val job = newJob()

        assertFailsWith<IllegalArgumentException> {
            job.expectValid(size = 100, parts = listOf(0L..50L, 50L..99L))
        }
    }

    @Test
    fun `getRequestedPartitionCount uses download item preference`() {
        val job = newJob(
            settings = DownloadSettings(defaultThreadCount = 8),
            item = newItem(preferredConnectionCount = 3),
        )

        assertEquals(3, job.getRequestedPartitionCount())
    }

    @Test
    fun `getRequestedPartitionCount falls back to manager default`() {
        val job = newJob(settings = DownloadSettings(defaultThreadCount = 6))

        assertEquals(6, job.getRequestedPartitionCount())
    }

    @Test
    fun `getMaxAllowedRetries uses manager setting by default`() {
        val job = newJob(settings = DownloadSettings(maxDownloadRetryCount = 4))

        assertEquals(4, job.getMaxAllowedRetries())
    }

    @Test
    fun `getMaxAllowedRetries uses job override when present`() {
        val job = newJob(settings = DownloadSettings(maxDownloadRetryCount = 4))

        job._maxAllowedRetries = 2

        assertEquals(2, job.getMaxAllowedRetries())
    }

    @Test
    fun `initializeDestination points to manager output file`() {
        val item = newItem(name = "video.mp4")
        val job = newJob(item = item)

        job.initializeDestination()

        assertEquals(File(dir, "video.mp4").canonicalFile, job.getDestination().outputFile)
    }

    private fun newJob(
        settings: DownloadSettings = DownloadSettings(),
        item: HttpDownloadItem = newItem(),
    ): HttpDownloadJob {
        val manager = DownloadManager(
            dlListDb = MemoryDownloadListDB(),
            partListDb = MemoryDownloadPartStatesDB(),
            settings = settings,
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

    private fun newItem(
        name: String = "file.bin",
        preferredConnectionCount: Int? = null,
    ) = HttpDownloadItem(
        link = "https://example.com/$name",
        id = 1,
        folder = dir.absolutePath,
        name = name,
        preferredConnectionCount = preferredConnectionCount,
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
