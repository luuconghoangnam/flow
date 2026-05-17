package com.flowspeed.link.android.storage

import com.flowspeed.link.shared.storage.IExtraDownloadItemSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class AndroidExtraDownloadItemSettings(
    override val id: Long,
    // turnOffWifi: Boolean
) : IExtraDownloadItemSettings {

    companion object : IExtraDownloadItemSettings.DataClassDefinitions<AndroidExtraDownloadItemSettings> {
        override fun createDefault(id: Long) = AndroidExtraDownloadItemSettings(id = id)
        override val serializer: KSerializer<AndroidExtraDownloadItemSettings> =
            AndroidExtraDownloadItemSettings.serializer()
    }
}
