package com.flowspeed.lib.util.desktop.utils.linux

import com.flowspeed.lib.util.desktop.DesktopUtils
import com.flowspeed.lib.util.desktop.keepawake.KeepAwake
import com.flowspeed.lib.util.desktop.poweraction.PowerAction
import com.flowspeed.lib.util.desktop.poweraction.PowerActionLinux
import com.flowspeed.lib.util.execAndWait

class LinuxUtils : DesktopUtils {
    private val keepAwake = KeepAwake.NoOpKeepAwake()
    private val powerActionForLinux = PowerActionLinux()
    override fun openSystemProxySettings() {
        val desktopEnv = System.getenv("XDG_CURRENT_DESKTOP")
        when {
            desktopEnv?.contains("GNOME") ?: false -> {
                execAndWait(
                    arrayOf(
                        "gnome-control-center network"
                    )
                )
            }

            desktopEnv?.contains("KDE") ?: false -> {
                execAndWait(
                    arrayOf(
                        "systemsettings5 proxy"
                    )
                )
            }

            else -> {
                println("Can't open System Proxy Settings: Unsupported desktop environment: $desktopEnv")
            }
        }
    }

    override fun powerAction(): PowerAction {
        return powerActionForLinux
    }

    override fun keepAwakeService(): KeepAwake {
        return keepAwake
    }
}
