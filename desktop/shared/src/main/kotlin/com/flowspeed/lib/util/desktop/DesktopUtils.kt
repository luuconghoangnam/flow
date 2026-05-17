package com.flowspeed.lib.util.desktop

import com.flowspeed.lib.util.desktop.keepawake.KeepAwake
import com.flowspeed.lib.util.desktop.poweraction.PowerAction
import com.flowspeed.lib.util.desktop.utils.linux.LinuxUtils
import com.flowspeed.lib.util.desktop.utils.mac.MacOSUtils
import com.flowspeed.lib.util.desktop.utils.windows.WindowsUtils
import com.flowspeed.lib.util.platform.Platform


interface DesktopUtils {
    fun openSystemProxySettings()
    fun powerAction(): PowerAction
    fun keepAwakeService(): KeepAwake

    companion object : DesktopUtils by getDesktopUtilOfCurrentOS()
}

private fun getDesktopUtilOfCurrentOS(): DesktopUtils {
    val platform = Platform.getCurrentPlatform() as Platform.Desktop
    return when (platform) {
        Platform.Desktop.Windows -> WindowsUtils()
        Platform.Desktop.MacOS -> MacOSUtils()
        Platform.Desktop.Linux -> LinuxUtils()
    }
}

