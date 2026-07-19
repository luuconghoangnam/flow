package com.flowspeed.lib.downloader.destination

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncompleteFileUtilTest {

    @Test
    fun `adds part extension to simple filename`() {
        val file = File("/downloads/video.mp4")
        val result = IncompleteFileUtil.addIncompleteIndicator(file, 42)
        assertEquals("video.mp4.dl-42.Flow.part", result.name)
        assertEquals(file.parentFile, result.parentFile)
    }

    @Test
    fun `does not double-add extension if already present`() {
        val file = File("/downloads/video.mp4.dl-42.Flow.part")
        val result = IncompleteFileUtil.addIncompleteIndicator(file, 42)
        assertEquals(file, result)
    }

    @Test
    fun `different download IDs produce different extensions`() {
        val file = File("/downloads/data.zip")
        val r1 = IncompleteFileUtil.addIncompleteIndicator(file, 1)
        val r2 = IncompleteFileUtil.addIncompleteIndicator(file, 999)
        assertTrue(r1.name.contains(".dl-1."))
        assertTrue(r2.name.contains(".dl-999."))
    }

    @Test
    fun `handles filename without extension`() {
        val file = File("/downloads/Makefile")
        val result = IncompleteFileUtil.addIncompleteIndicator(file, 7)
        assertEquals("Makefile.dl-7.Flow.part", result.name)
    }

    @Test
    fun `handles filename with multiple dots`() {
        val file = File("/downloads/archive.tar.gz")
        val result = IncompleteFileUtil.addIncompleteIndicator(file, 3)
        assertEquals("archive.tar.gz.dl-3.Flow.part", result.name)
    }

    @Test
    fun `preserves parent directory`() {
        val file = File("/home/user/Downloads/file.iso")
        val result = IncompleteFileUtil.addIncompleteIndicator(file, 100)
        assertEquals(file.parentFile, result.parentFile)
    }
}
