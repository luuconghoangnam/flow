package com.flowspeed.lib.downloader.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class NumUtilTest {

    @Test
    fun `calcPercent Long - 0 percent`() {
        assertEquals(0, calcPercent(0L, 100L))
    }

    @Test
    fun `calcPercent Long - 50 percent`() {
        assertEquals(50, calcPercent(50L, 100L))
    }

    @Test
    fun `calcPercent Long - 100 percent`() {
        assertEquals(100, calcPercent(100L, 100L))
    }

    @Test
    fun `calcPercent Long - truncates decimal`() {
        // 33.33% → 33
        assertEquals(33, calcPercent(1L, 3L))
    }

    @Test
    fun `calcPercent Long - large file sizes`() {
        // 500MB of 1GB = 50%
        assertEquals(50, calcPercent(500_000_000L, 1_000_000_000L))
    }

    @Test
    fun `calcPercent Long - nearly complete`() {
        // 999 of 1000 = 99%
        assertEquals(99, calcPercent(999L, 1000L))
    }

    @Test
    fun `calcPercent Int - basic`() {
        assertEquals(75, calcPercent(75, 100))
    }

    @Test
    fun `calcPercent Double - basic`() {
        assertEquals(25, calcPercent(25.0, 100.0))
    }

    @Test
    fun `calcPercent Double - fractional input`() {
        assertEquals(50, calcPercent(0.5, 1.0))
    }
}
