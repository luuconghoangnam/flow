package com.flowspeed.lib.util.desktop.utils.windows

import com.flowspeed.lib.util.desktop.DesktopUtils
import com.flowspeed.lib.util.desktop.keepawake.KeepAwake
import com.flowspeed.lib.util.desktop.keepawake.WindowsKeepAwake
import com.flowspeed.lib.util.desktop.poweraction.PowerAction
import com.flowspeed.lib.util.desktop.poweraction.PowerActionWindows
import com.flowspeed.lib.util.execAndWait

class WindowsUtils : DesktopUtils {
    private val keepAwakeService = WindowsKeepAwake()
    private val powerActionWindows = PowerActionWindows()
    override fun openSystemProxySettings() {
        val result = execAndWait(
            arrayOf(
                "cmd", "/c", "start",
                "ms-settings:network-proxy",
            )
        )
        if (!result) {
            execAndWait(
                arrayOf(
                    "rundll32.exe shell32.dll,Control_RunDLL inetcpl.cpl,,4"
                )
            )
        }
    }

    override fun powerAction(): PowerAction {
        return powerActionWindows
    }

    override fun keepAwakeService(): KeepAwake {
        return keepAwakeService
    }
}
