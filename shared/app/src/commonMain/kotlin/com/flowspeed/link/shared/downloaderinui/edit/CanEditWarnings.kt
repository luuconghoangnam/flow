package com.flowspeed.link.shared.downloaderinui.edit

import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.util.convertDurationToHumanReadable
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.asStringSourceWithARgs

sealed interface CanEditWarnings {
    fun asStringSource(): StringSource
    data class FileSizeNotMatch(
        val currentSize: Long,
        val newSize: Long,
    ) : CanEditWarnings {
        override fun asStringSource(): StringSource {
            return Res.string.edit_download_saved_download_item_size_not_match
                .asStringSourceWithARgs(
                    Res.string.edit_download_saved_download_item_size_not_match_createArgs(
                        currentSize = "$currentSize",
                        newSize = "$newSize",
                    )
                )
        }

    }
    data class DurationNotMatch(
        val currentDuration: Double?,
        val newDuration: Double?,
    ) : CanEditWarnings {
        val notAvailableString = Res.string.unknown.asStringSource()
        override fun asStringSource(): StringSource {
            val currentDurationString = currentDuration?.let {
                convertDurationToHumanReadable(it)
            } ?: notAvailableString
            val newDurationString = newDuration?.let {
                convertDurationToHumanReadable(it)
            } ?: notAvailableString
            return Res.string.edit_download_saved_download_item_size_not_match
                .asStringSourceWithARgs(
                    Res.string.edit_download_saved_download_item_size_not_match_createArgs(
                        currentSize = currentDurationString.getString(),
                        newSize = newDurationString.getString(),
                    )
                )
        }

    }
}
