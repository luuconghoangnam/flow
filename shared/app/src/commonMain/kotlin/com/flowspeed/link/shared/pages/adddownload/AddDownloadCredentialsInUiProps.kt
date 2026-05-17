package com.flowspeed.link.shared.pages.adddownload

import com.flowspeed.link.shared.util.FilenameFixer
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import kotlinx.serialization.Serializable

@Serializable
data class AddDownloadCredentialsInUiProps(
    val credentials: IDownloadCredentials,
    val extraConfig: Configs = Configs(),
) {
    @Serializable
    data class Configs(
        // don't consume it directly as it might not be a valid file name on user's current OS
        val suggestedName: String? = null,
    ) {
        fun getAndFixSuggestedName(): String? {
            return suggestedName?.let(FilenameFixer::fix)
        }
    }
}
