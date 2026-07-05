package com.flowspeed.lib.downloader.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitToRangeTest {

    // ---------------------------------------------------------------------------
    // Basic correctness
    // ---------------------------------------------------------------------------

    @Test
    fun `single part when size equals minPartSize`() {
        val result = splitToRange(size = 100, minPartSize = 100, maxPartCount = 8)
        assertEquals(1, result.size)
        assertEquals(0L..99L, result[0])
    }

    @Test
    fun `single part when maxPartCount is 1`() {
        val result = splitToRange(size = 1000, minPartSize = 100, maxPartCount = 1)
        assertEquals(1, result.size)
        assertEquals(0L..999L, result[0])
    }

    @Test
    fun `splits into 8 parts evenly divisible`() {
        val result = splitToRange(size = 800, minPartSize = 1, maxPartCount = 8)
        assertEquals(8, result.size)
        result.forEachIndexed { i, range ->
            assertEquals(100L, range.last - range.first + 1, "part $i should be 100 bytes")
        }
    }

    @Test
    fun `splits into correct count when size is not evenly divisible`() {
        // 100 bytes, 3 parts → 34, 33, 33
        val result = splitToRange(size = 100, minPartSize = 1, maxPartCount = 3)
        assertEquals(3, result.size)
        val sizes = result.map { it.last - it.first + 1 }
        assertEquals(100L, sizes.sum(), "total coverage must equal file size")
    }

    @Test
    fun `minimal size of 1 byte`() {
        val result = splitToRange(size = 1, minPartSize = 1, maxPartCount = 8)
        assertEquals(1, result.size)
        assertEquals(0L..0L, result[0])
    }

    // ---------------------------------------------------------------------------
    // Coverage — ranges must be contiguous and cover [0, size-1]
    // ---------------------------------------------------------------------------

    @Test
    fun `ranges are contiguous and cover full file`() {
        listOf(
            Triple(1_000_000L, 64_000L, 16L),   // typical large file
            Triple(500L, 100L, 8L),               // small file
            Triple(777L, 1L, 7L),                 // odd size
            Triple(1L, 1L, 1L),                   // 1 byte
        ).forEach { (size, minPart, maxCount) ->
            val ranges = splitToRange(size, minPart, maxCount)
            assertContinuous(ranges, size, "size=$size minPart=$minPart maxCount=$maxCount")
        }
    }

    // ---------------------------------------------------------------------------
    // minPartSize constraint
    // ---------------------------------------------------------------------------

    @Test
    fun `number of parts never exceeds what minPartSize allows`() {
        // size=100, minPartSize=40 → max possible parts = ceil(100/40) = 3
        val result = splitToRange(size = 100, minPartSize = 40, maxPartCount = 8)
        assertTrue(result.size <= 3, "parts should not exceed size/minPartSize ceiling")
        assertContinuous(result, 100)
    }

    @Test
    fun `minPartSize larger than size produces single part`() {
        val result = splitToRange(size = 50, minPartSize = 200, maxPartCount = 8)
        assertEquals(1, result.size)
        assertEquals(0L..49L, result[0])
    }

    // ---------------------------------------------------------------------------
    // maxPartCount constraint
    // ---------------------------------------------------------------------------

    @Test
    fun `actual count never exceeds maxPartCount`() {
        for (maxCount in 1L..16L) {
            val result = splitToRange(size = 10_000, minPartSize = 1, maxPartCount = maxCount)
            assertTrue(result.size <= maxCount, "count=${result.size} must be <= maxPartCount=$maxCount")
        }
    }

    // ---------------------------------------------------------------------------
    // Error conditions
    // ---------------------------------------------------------------------------

    @Test
    fun `throws on size zero`() {
        var threw = false
        try { splitToRange(0, 1, 8) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw, "size=0 must throw")
    }

    @Test
    fun `throws on minPartSize zero`() {
        var threw = false
        try { splitToRange(100, 0, 8) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw, "minPartSize=0 must throw")
    }

    @Test
    fun `throws on maxPartCount zero`() {
        var threw = false
        try { splitToRange(100, 1, 0) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw, "maxPartCount=0 must throw")
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private fun assertContinuous(ranges: List<LongRange>, totalSize: Long, hint: String = "") {
        assertTrue(ranges.isNotEmpty(), "ranges must not be empty $hint")
        assertEquals(0L, ranges.first().first, "must start at 0 $hint")
        assertEquals(totalSize - 1, ranges.last().last, "must end at size-1 $hint")
        for (i in 1 until ranges.size) {
            assertEquals(
                ranges[i - 1].last + 1,
                ranges[i].first,
                "gap or overlap between part ${i - 1} and $i $hint"
            )
        }
    }
}
