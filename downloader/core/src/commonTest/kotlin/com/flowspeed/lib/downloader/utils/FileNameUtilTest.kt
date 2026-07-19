package com.flowspeed.lib.downloader.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileNameUtilTest {

    // ---------------------------------------------------------------------------
    // getExtensionOrNull
    // ---------------------------------------------------------------------------

    @Test
    fun `getExtensionOrNull returns extension for simple file`() {
        assertEquals("mp4", FileNameUtil.getExtensionOrNull("video.mp4"))
    }

    @Test
    fun `getExtensionOrNull returns extension for file with multiple dots`() {
        assertEquals("gz", FileNameUtil.getExtensionOrNull("archive.tar.gz"))
    }

    @Test
    fun `getExtensionOrNull returns null for file with no extension`() {
        assertNull(FileNameUtil.getExtensionOrNull("Makefile"))
    }

    @Test
    fun `getExtensionOrNull returns null for empty string`() {
        assertNull(FileNameUtil.getExtensionOrNull(""))
    }

    @Test
    fun `getExtensionOrNull returns null for filename ending with dot`() {
        // trailing dot means empty extension
        assertNull(FileNameUtil.getExtensionOrNull("file."))
    }

    @Test
    fun `getExtensionOrNull handles hidden files (dot prefix)`() {
        // ".gitignore" — substringAfterLast('.') = "gitignore"
        assertEquals("gitignore", FileNameUtil.getExtensionOrNull(".gitignore"))
    }

    // ---------------------------------------------------------------------------
    // replaceExtension
    // ---------------------------------------------------------------------------

    @Test
    fun `replaceExtension replaces existing extension`() {
        assertEquals("video.webm", FileNameUtil.replaceExtension("video.mp4", "webm"))
    }

    @Test
    fun `replaceExtension replaces last extension only for multi-dot name`() {
        // "archive.tar.gz" → drop "gz" → "archive.tar." → append "bz2"
        assertEquals("archive.tar.bz2", FileNameUtil.replaceExtension("archive.tar.gz", "bz2"))
    }

    @Test
    fun `replaceExtension appends when no extension and appendIfNotExists true`() {
        assertEquals("Makefile.sh", FileNameUtil.replaceExtension("Makefile", "sh", appendIfNotExists = true))
    }

    @Test
    fun `replaceExtension leaves unchanged when no extension and appendIfNotExists false`() {
        assertEquals("Makefile", FileNameUtil.replaceExtension("Makefile", "sh", appendIfNotExists = false))
    }

    // ---------------------------------------------------------------------------
    // numberedIfExists — uses real temp files to simulate conflicts
    // ---------------------------------------------------------------------------

    @Test
    fun `numberedIfExists emits original filename when file does not exist`() = runTest {
        val dir = Files.createTempDirectory("fnutil-test").toFile()
        try {
            val target = File(dir, "download.mp4")
            // target does NOT exist → first emission should be target itself
            val first = FileNameUtil.numberedIfExists(target).first()
            assertEquals(target, first)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `numberedIfExists skips existing file and emits numbered variant`() = runTest {
        val dir = Files.createTempDirectory("fnutil-test").toFile()
        try {
            val target = File(dir, "download.mp4")
            target.createNewFile() // simulate conflict

            val first = FileNameUtil.numberedIfExists(target).first()
            assertEquals(File(dir, "download_1.mp4"), first)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `numberedIfExists skips multiple conflicts and finds first free slot`() = runTest {
        val dir = Files.createTempDirectory("fnutil-test").toFile()
        try {
            val target = File(dir, "file.zip")
            target.createNewFile()
            File(dir, "file_1.zip").createNewFile()
            File(dir, "file_2.zip").createNewFile()

            val first = FileNameUtil.numberedIfExists(target).first()
            assertEquals(File(dir, "file_3.zip"), first)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `numberedIfExists works for files with no extension`() = runTest {
        val dir = Files.createTempDirectory("fnutil-test").toFile()
        try {
            val target = File(dir, "Makefile")
            target.createNewFile()

            val first = FileNameUtil.numberedIfExists(target).first()
            assertEquals(File(dir, "Makefile_1"), first)
        } finally {
            dir.deleteRecursively()
        }
    }
}
