package com.flowspeed.link.shared.storage

import com.flowspeed.lib.util.datasize.CommonSizeConvertConfigs
import com.flowspeed.lib.util.datasize.ConvertSizeConfig

enum class SupportedSizeUnits {
    BinaryBits,
    BinaryBytes,
    DecimalBits,
    DecimalBytes;

    fun toConfig(): ConvertSizeConfig {
        return when (this) {
            BinaryBits -> CommonSizeConvertConfigs.BinaryBits
            BinaryBytes -> CommonSizeConvertConfigs.BinaryBytes
            DecimalBits -> CommonSizeConvertConfigs.DecimalBits
            DecimalBytes -> CommonSizeConvertConfigs.DecimalBytes
        }
    }

    companion object {
        fun fromConfig(config: ConvertSizeConfig): SupportedSizeUnits? {
            return when (config) {
                CommonSizeConvertConfigs.BinaryBits -> BinaryBits
                CommonSizeConvertConfigs.BinaryBytes -> BinaryBytes
                CommonSizeConvertConfigs.DecimalBits -> DecimalBits
                CommonSizeConvertConfigs.DecimalBytes -> DecimalBytes
                else -> null
            }
        }
    }
}
