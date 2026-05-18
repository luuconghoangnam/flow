package com.flowspeed.link.shared.util.downloadlocation

import com.flowspeed.link.shared.util.SystemDownloadLocationProvider

object PlatformDownloadLocationProvider {
    val instance: SystemDownloadLocationProvider by lazy {
        getPlatformDownloadLocationProvider()
    }
}

expect fun getPlatformDownloadLocationProvider(): SystemDownloadLocationProvider

