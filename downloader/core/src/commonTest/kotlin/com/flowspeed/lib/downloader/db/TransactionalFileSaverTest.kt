package com.flowspeed.lib.downloader.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class TestData(val name: String, val value: Int)

class TransactionalFileSaverTest {

    private val json = Json { prettyPrint = false }
    private val saver = TransactionalFileSaver(json)
    private lateinit var dir: File

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("tsaver-test").toFile()
    }

    @AfterTest
    fun teardown() {
        dir.deleteRecursively()
    }

    // ---------------------------------------------------------------------------
    // writeText / readText roundtrip
    // ---------------------------------------------------------------------------

    @Test
    fun `writeText then readText returns same content`() {
        val file = File(dir, "data.txt")
        saver.writeText(file, "hello world")
        assertEquals("hello world", saver.readText(file))
    }

    @Test
    fun `writeText overwrites existing content`() {
        val file = File(dir, "data.txt")
        saver.writeText(file, "first")
        saver.writeText(file, "second")
        assertEquals("second", saver.readText(file))
    }

    @Test
    fun `readText returns null when file does not exist`() {
        val file = File(dir, "missing.txt")
        assertNull(saver.readText(file))
    }

    // ---------------------------------------------------------------------------
    // writeObject / readObject roundtrip (reified)
    // ---------------------------------------------------------------------------

    @Test
    fun `writeObject then readObject returns equal object`() {
        val file = File(dir, "obj.json")
        val original = TestData("download", 42)
        saver.writeObject(file, original)
        val result = saver.readObject<TestData>(file)
        assertEquals(original, result)
    }

    @Test
    fun `readObject returns null when file does not exist`() {
        val file = File(dir, "missing.json")
        val result = saver.readObject<TestData>(file)
        assertNull(result)
    }

    @Test
    fun `readObject returns null when file is corrupted`() {
        val file = File(dir, "bad.json")
        file.writeText("{ this is not valid json }")
        val result = saver.readObject<TestData>(file)
        assertNull(result)
    }

    // ---------------------------------------------------------------------------
    // Atomicity — temp file behavior
    // ---------------------------------------------------------------------------

    @Test
    fun `temp bak file does not exist after successful write`() {
        val file = File(dir, "data.txt")
        saver.writeText(file, "content")
        val bakFile = saver.getBakFile(file)
        assertFalse(bakFile.exists(), "tmp file should be removed after successful write")
    }

    @Test
    fun `final file exists after write`() {
        val file = File(dir, "data.txt")
        saver.writeText(file, "content")
        assertTrue(file.exists(), "final file must exist after write")
    }

    @Test
    fun `getBakFile returns path with tmp suffix`() {
        val file = File(dir, "data.json")
        val bak = saver.getBakFile(file)
        assertTrue(bak.path.endsWith(".tmp"), "bak file should end with .tmp")
    }

    // ---------------------------------------------------------------------------
    // Multiple objects — isolation between files
    // ---------------------------------------------------------------------------

    @Test
    fun `writing multiple files does not interfere`() {
        val file1 = File(dir, "a.json")
        val file2 = File(dir, "b.json")
        saver.writeObject(file1, TestData("alpha", 1))
        saver.writeObject(file2, TestData("beta", 2))

        assertEquals(TestData("alpha", 1), saver.readObject<TestData>(file1))
        assertEquals(TestData("beta", 2), saver.readObject<TestData>(file2))
    }

    @Test
    fun `overwriting preserves sibling files`() {
        val file1 = File(dir, "a.json")
        val file2 = File(dir, "b.json")
        saver.writeObject(file1, TestData("alpha", 1))
        saver.writeObject(file2, TestData("beta", 2))
        saver.writeObject(file1, TestData("alpha-v2", 99))

        assertEquals(TestData("alpha-v2", 99), saver.readObject<TestData>(file1))
        assertEquals(TestData("beta", 2), saver.readObject<TestData>(file2))
    }

    // ---------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------

    @Test
    fun `writeText with empty string is readable`() {
        val file = File(dir, "empty.txt")
        saver.writeText(file, "")
        assertEquals("", saver.readText(file))
    }

    @Test
    fun `writeText with unicode content roundtrips correctly`() {
        val file = File(dir, "unicode.txt")
        val content = "Tải xuống 下载 ダウンロード 🔥"
        saver.writeText(file, content)
        assertEquals(content, saver.readText(file))
    }

    @Test
    fun `writeObject with serializer overload returns same data`() {
        val file = File(dir, "serialized.json")
        val original = TestData("explicit-serializer", 7)
        saver.writeObject(file, original, TestData.serializer())
        val result = saver.readObject(file, TestData.serializer())
        assertNotNull(result)
        assertEquals(original, result)
    }
}
