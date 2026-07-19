package com.flowspeed.lib.downloader.connection.response

import com.flowspeed.lib.downloader.exception.UnSuccessfulResponseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpResponseInfoTest {

    @Test
    fun `partial response exposes total length and resume support`() {
        val response = response(
            statusCode = 206,
            headers = mapOf(
                "content-length" to "256",
                "content-range" to "bytes 0-255/1000",
            ),
        )

        assertTrue(response.isPartial)
        assertTrue(response.resumeSupport)
        assertEquals(1000, response.totalLength)
    }

    @Test
    fun `partial response without content range does not imply resume support`() {
        val response = response(
            statusCode = 206,
            headers = mapOf("content-length" to "256"),
        )

        assertNull(response.contentRange)
        assertFalse(response.resumeSupport)
        assertEquals(256, response.totalLength)
    }

    @Test
    fun `malformed content range is ignored`() {
        val response = response(
            statusCode = 206,
            headers = mapOf(
                "content-length" to "256",
                "content-range" to "bytes invalid/1000",
            ),
        )

        assertNull(response.contentRange)
        assertFalse(response.resumeSupport)
    }

    @Test
    fun `full response uses content length as total length`() {
        val response = response(
            statusCode = 200,
            headers = mapOf("content-length" to "1000"),
        )

        assertFalse(response.isPartial)
        assertFalse(response.resumeSupport)
        assertEquals(1000, response.totalLength)
    }

    @Test
    fun `unsuccessful response is rejected`() {
        val response = response(statusCode = 404, message = "Not Found")

        assertFalse(response.isSuccessFul)
        val error = assertFailsWith<UnSuccessfulResponseException> {
            response.expectSuccess()
        }
        assertEquals(404, error.code)
    }

    @Test
    fun `html content type is recognized ignoring value casing`() {
        val response = response(
            statusCode = 200,
            headers = mapOf("content-type" to "Text/HTML; charset=UTF-8"),
        )

        assertTrue(response.isWebPage)
    }

    @Test
    fun `content range parses standard and prefixless values`() {
        val standard = response(
            statusCode = 206,
            headers = mapOf("content-range" to "bytes 100-199/1000"),
        )
        val prefixless = response(
            statusCode = 206,
            headers = mapOf("content-range" to "100-199/1000"),
        )

        assertEquals(100L..199L, standard.contentRange?.range)
        assertEquals(1000, standard.contentRange?.fullSize)
        assertEquals(standard.contentRange, prefixless.contentRange)
    }

    @Test
    fun `content range parses wildcard range and size`() {
        val wildcardRange = response(
            statusCode = 416,
            headers = mapOf("content-range" to "bytes */1000"),
        )
        val wildcardSize = response(
            statusCode = 206,
            headers = mapOf("content-range" to "bytes 100-199/*"),
        )

        assertNull(wildcardRange.contentRange?.range)
        assertEquals(1000, wildcardRange.contentRange?.fullSize)
        assertEquals(100L..199L, wildcardSize.contentRange?.range)
        assertNull(wildcardSize.contentRange?.fullSize)
    }

    @Test
    fun `content range rejects invalid numbers`() {
        val response = response(
            statusCode = 206,
            headers = mapOf("content-range" to "bytes one-199/1000"),
        )

        assertNull(response.contentRange)
    }

    @Test
    fun `content range currently preserves reversed range`() {
        val response = response(
            statusCode = 206,
            headers = mapOf("content-range" to "bytes 199-100/1000"),
        )

        assertEquals(199L..100L, response.contentRange?.range)
    }

    private fun response(
        statusCode: Int,
        message: String = "OK",
        headers: Map<String, String> = emptyMap(),
    ) = HttpResponseInfo(
        statusCode = statusCode,
        message = message,
        requestUrl = "https://example.com/file.bin",
        responseHeaders = headers,
    )
}
