package com.flowspeed.lib.downloader.part

import com.flowspeed.lib.downloader.connection.Connection
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.IHttpDownloadCredentials
import com.flowspeed.lib.downloader.exception.ServerPartIsNotTheSameAsWeExpectException
import com.flowspeed.lib.downloader.exception.UnSuccessfulResponseException
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpPartDownloaderTest {

    @Test
    fun `matching response length passes validation`() = runTest {
        val fixture = fixture(contentLength = 100)

        val connection = fixture.downloader.connectAndVerify()

        assertSame(fixture.source, connection.source)
        assertSame(fixture.connection.responseInfo, connection.responseInfo)
        assertFalse(fixture.source.closed)
        connection.close()
        assertTrue(fixture.source.closed)
    }

    @Test
    fun `short response passes when content range starts at requested offset`() = runTest {
        val fixture = fixture(
            contentLength = 50,
            responseHeaders = mapOf("content-range" to "bytes 0-49/1000"),
        )

        val connection = fixture.downloader.connectAndVerify()

        assertSame(fixture.source, connection.source)
        assertSame(fixture.connection.responseInfo, connection.responseInfo)
        assertFalse(fixture.source.closed)
        connection.close()
        assertTrue(fixture.source.closed)
    }

    @Test
    fun `short response with wrong start fails and closes connection`() = runTest {
        val fixture = fixture(
            contentLength = 50,
            responseHeaders = mapOf("content-range" to "bytes 10-59/1000"),
        )

        assertFailsWith<ServerPartIsNotTheSameAsWeExpectException> {
            fixture.downloader.connectAndVerify()
        }
        assertTrue(fixture.source.closed)
    }

    @Test
    fun `short response without content range fails and closes connection`() = runTest {
        val fixture = fixture(contentLength = 50)

        assertFailsWith<ServerPartIsNotTheSameAsWeExpectException> {
            fixture.downloader.connectAndVerify()
        }
        assertTrue(fixture.source.closed)
    }

    @Test
    fun `non-strict mode currently rejects length mismatch`() = runTest {
        val fixture = fixture(contentLength = 50, strictMode = false)

        assertFailsWith<ServerPartIsNotTheSameAsWeExpectException> {
            fixture.downloader.connectAndVerify()
        }
        assertTrue(fixture.source.closed)
    }

    @Test
    fun `unsuccessful response fails and closes connection`() = runTest {
        val fixture = fixture(contentLength = 100, statusCode = 500)

        assertFailsWith<UnSuccessfulResponseException> {
            fixture.downloader.connectAndVerify()
        }
        assertTrue(fixture.source.closed)
    }

    @Test
    fun `stopped downloader closes connection and throws cancellation`() = runTest {
        val fixture = fixture(contentLength = 100)
        fixture.downloader.stop = true

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            fixture.downloader.connectAndVerify()
        }
        assertTrue(fixture.source.closed)
    }

    private fun fixture(
        contentLength: Long,
        responseHeaders: Map<String, String> = emptyMap(),
        statusCode: Int = 206,
        strictMode: Boolean = true,
    ): Fixture {
        val source = TrackingSource()
        val response = HttpResponseInfo(
            statusCode = statusCode,
            message = if (statusCode in 200..299) "OK" else "Server Error",
            requestUrl = "https://example.com/file.bin",
            responseHeaders = responseHeaders,
        )
        val connection = Connection(
            source = source,
            contentLength = contentLength,
            responseInfo = response,
        )
        val client = FakeHttpDownloaderClient(connection)
        val downloader = HttpPartDownloader(
            credentials = HttpDownloadCredentials("https://example.com/file.bin"),
            getDestWriter = { error("connectAndVerify must not request destination writer") },
            part = RangedPart(from = 0, to = 99),
            client = client,
            speedLimiters = emptyList(),
            strictMode = strictMode,
            partSplitLock = Any(),
        )
        return Fixture(downloader, connection, source)
    }

    private data class Fixture(
        val downloader: HttpPartDownloader,
        val connection: Connection<HttpResponseInfo>,
        val source: TrackingSource,
    )

    private class FakeHttpDownloaderClient(
        private val connection: Connection<HttpResponseInfo>,
    ) : HttpDownloaderClient() {
        override suspend fun actualHead(
            credentials: IHttpDownloadCredentials,
            start: Long?,
            end: Long?,
        ): HttpResponseInfo = connection.responseInfo

        override suspend fun actualConnect(
            credentials: IHttpBasedDownloadCredentials,
            start: Long?,
            end: Long?,
        ): Connection<HttpResponseInfo> = connection
    }

    private class TrackingSource : Source by Buffer() {
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
