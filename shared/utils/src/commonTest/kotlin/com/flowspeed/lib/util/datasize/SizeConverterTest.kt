package com.flowspeed.lib.util.datasize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SizeConverterTest {

    // ---------------------------------------------------------------------------
    // bytesToSize — binary (KiB, MiB, GiB)
    // ---------------------------------------------------------------------------

    @Test
    fun `0 bytes stays as bytes`() {
        val result = SizeConverter.bytesToSize(0, CommonSizeConvertConfigs.BinaryBytes)
        assertEquals(0.0, result.value)
        assertEquals(SizeFactors.FactorValue.None, result.unit.factorValue)
    }

    @Test
    fun `1023 bytes stays as bytes`() {
        val result = SizeConverter.bytesToSize(1023, CommonSizeConvertConfigs.BinaryBytes)
        assertEquals(SizeFactors.FactorValue.None, result.unit.factorValue)
        assertEquals(1023.0, result.value, 0.01)
    }

    @Test
    fun `1024 bytes becomes 1 KiB`() {
        val result = SizeConverter.bytesToSize(1024, CommonSizeConvertConfigs.BinaryBytes)
        assertEquals(SizeFactors.FactorValue.Kilo, result.unit.factorValue)
        assertEquals(1.0, result.value, 0.01)
    }

    @Test
    fun `1 MiB in bytes`() {
        val bytes = 1024L * 1024L
        val result = SizeConverter.bytesToSize(bytes, CommonSizeConvertConfigs.BinaryBytes)
        assertEquals(SizeFactors.FactorValue.Mega, result.unit.factorValue)
        assertEquals(1.0, result.value, 0.01)
    }

    @Test
    fun `1 GiB in bytes`() {
        val bytes = 1024L * 1024L * 1024L
        val result = SizeConverter.bytesToSize(bytes, CommonSizeConvertConfigs.BinaryBytes)
        assertEquals(SizeFactors.FactorValue.Giga, result.unit.factorValue)
        assertEquals(1.0, result.value, 0.01)
    }

    @Test
    fun `1500 MiB shows as GiB`() {
        val bytes = 1500L * 1024L * 1024L
        val result = SizeConverter.bytesToSize(bytes, CommonSizeConvertConfigs.BinaryBytes)
        assertEquals(SizeFactors.FactorValue.Giga, result.unit.factorValue)
        assertTrue(result.value > 1.4 && result.value < 1.5, "expected ~1.46 GiB, got ${result.value}")
    }

    // ---------------------------------------------------------------------------
    // bytesToSize — decimal (KB, MB, GB)
    // ---------------------------------------------------------------------------

    @Test
    fun `1000 bytes becomes 1 KB decimal`() {
        val result = SizeConverter.bytesToSize(1000, CommonSizeConvertConfigs.DecimalBytes)
        assertEquals(SizeFactors.FactorValue.Kilo, result.unit.factorValue)
        assertEquals(1.0, result.value, 0.01)
    }

    @Test
    fun `1 GB decimal`() {
        val result = SizeConverter.bytesToSize(1_000_000_000, CommonSizeConvertConfigs.DecimalBytes)
        assertEquals(SizeFactors.FactorValue.Giga, result.unit.factorValue)
        assertEquals(1.0, result.value, 0.01)
    }

    // ---------------------------------------------------------------------------
    // sizeToBytes — roundtrip
    // ---------------------------------------------------------------------------

    @Test
    fun `1 KiB to bytes`() {
        val sizeWithUnit = SizeWithUnit(1.0, SizeUnit(SizeFactors.FactorValue.Kilo, BaseSize.Bytes, SizeFactors.BinarySizeFactors))
        val bytes = SizeConverter.sizeToBytes(sizeWithUnit)
        assertEquals(1024L, bytes)
    }

    @Test
    fun `1 MiB to bytes`() {
        val sizeWithUnit = SizeWithUnit(1.0, SizeUnit(SizeFactors.FactorValue.Mega, BaseSize.Bytes, SizeFactors.BinarySizeFactors))
        val bytes = SizeConverter.sizeToBytes(sizeWithUnit)
        assertEquals(1024L * 1024L, bytes)
    }

    @Test
    fun `5 GiB to bytes`() {
        val sizeWithUnit = SizeWithUnit(5.0, SizeUnit(SizeFactors.FactorValue.Giga, BaseSize.Bytes, SizeFactors.BinarySizeFactors))
        val bytes = SizeConverter.sizeToBytes(sizeWithUnit)
        assertEquals(5L * 1024L * 1024L * 1024L, bytes)
    }

    // ---------------------------------------------------------------------------
    // toString formatting
    // ---------------------------------------------------------------------------

    @Test
    fun `SizeUnit toString for binary bytes KiB`() {
        val unit = SizeUnit(SizeFactors.FactorValue.Kilo, BaseSize.Bytes, SizeFactors.BinarySizeFactors)
        assertEquals("KiB", unit.toString())
    }

    @Test
    fun `SizeUnit toString for decimal bytes MB`() {
        val unit = SizeUnit(SizeFactors.FactorValue.Mega, BaseSize.Bytes, SizeFactors.DecimalSizeFactors)
        assertEquals("MB", unit.toString())
    }

    @Test
    fun `SizeWithUnit toString formatted`() {
        val size = SizeWithUnit(2.5, SizeUnit(SizeFactors.FactorValue.Giga, BaseSize.Bytes, SizeFactors.BinarySizeFactors))
        assertEquals("2.5 GiB", size.toString())
    }
}
