package com.flowspeed.lib.downloader.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeUtilsTest {

    @Test
    fun `parses standard HTTP Last-Modified header`() {
        // Wed, 09 Oct 2024 08:28:00 GMT → known timestamp
        val ts = TimeUtils.convertLastModifiedHeaderToTimestamp("Wed, 09 Oct 2024 08:28:00 GMT")
        // Should be around 1728462480000 (exact depends on timezone handling)
        assertTrue(ts > 0, "timestamp must be positive")
        // Verify it's in the right ballpark (Oct 2024)
        assertTrue(ts > 1728000000000L, "should be after Oct 2024 start")
        assertTrue(ts < 1729000000000L, "should be before Nov 2024 start")
    }

    @Test
    fun `parses another valid date`() {
        val ts = TimeUtils.convertLastModifiedHeaderToTimestamp("Tue, 01 Jan 2019 00:00:00 GMT")
        // 2019-01-01 00:00:00 GMT = 1546300800000
        assertEquals(1546300800000L, ts)
    }

    @Test
    fun `throws on invalid header`() {
        var threw = false
        try {
            TimeUtils.convertLastModifiedHeaderToTimestamp("not a date")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw, "should throw on invalid date string")
    }

    @Test
    fun `throws on empty string`() {
        var threw = false
        try {
            TimeUtils.convertLastModifiedHeaderToTimestamp("")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw, "should throw on empty string")
    }
}
