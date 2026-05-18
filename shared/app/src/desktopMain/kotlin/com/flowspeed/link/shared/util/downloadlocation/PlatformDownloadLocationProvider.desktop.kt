package com.flowspeed.link.shared.util.downloadlocation

import com.flowspeed.link.shared.util.SystemDownloadLocationProvider
import com.flowspeed.lib.util.platform.Platform
import com.flowspeed.lib.util.platform.asDesktop

actual fun getPlatformDownloadLocationProvider(): SystemDownloadLocationProvider {
    return when (Platform.asDesktop()) {
        Platform.Desktop.Windows -> WindowsDownloadLocationProvider()
        Platform.Desktop.Linux -> LinuxDownloadLocationProvider()
        Platform.Desktop.MacOS -> MacDownloadLocationProvider()
    }
}
