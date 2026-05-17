package com.flowspeed.link.shared.pages.enterurl

import com.flowspeed.link.shared.downloaderinui.TADownloaderInUI

sealed interface DownloaderSelection {
    data object Auto : DownloaderSelection
    data class Fixed(
        val downloaderInUi: TADownloaderInUI,
    ) : DownloaderSelection
}
