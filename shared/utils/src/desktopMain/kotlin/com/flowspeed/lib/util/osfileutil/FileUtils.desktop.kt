package com.flowspeed.lib.util.osfileutil

import com.flowspeed.lib.util.platform.Platform
import com.flowspeed.lib.util.platform.asDesktop

actual fun getPlatformFileUtil(): FileUtils {
    return when (Platform.asDesktop()) {
        Platform.Desktop.Windows -> WindowsFileUtils()
        Platform.Desktop.Linux -> LinuxFileUtils()
        Platform.Desktop.MacOS -> MacOsFileUtils()
    }
}
