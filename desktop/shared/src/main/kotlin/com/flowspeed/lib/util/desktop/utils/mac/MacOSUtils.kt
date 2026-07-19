package com.flowspeed.lib.util.desktop.utils.mac

import com.flowspeed.lib.util.desktop.DesktopUtils
import com.flowspeed.lib.util.desktop.keepawake.KeepAwake
import com.flowspeed.lib.util.desktop.keepawake.MacKeepAwake
import com.flowspeed.lib.util.desktop.poweraction.PowerAction
import com.flowspeed.lib.util.desktop.poweraction.PowerActionMac
import com.flowspeed.lib.util.execAndWait

class MacOSUtils : DesktopUtils {
    private val keepAwakeService = MacKeepAwake()
    private val powerActionForMac = PowerActionMac()
    override fun openSystemProxySettings() {
        val commands = listOf(
            arrayOf("open", "x-apple.systempreferences:com.apple.Network-Settings.extension"),
            arrayOf("open", "/System/Library/PreferencePanes/Network.prefPane"),
            arrayOf("open", "/System/Preferences/Network")
        )

        for (command in commands) {
            if (execAndWait(command)) return
        }
    }

    override fun powerAction(): PowerAction {
        return powerActionForMac
    }

    override fun keepAwakeService(): KeepAwake {
        return keepAwakeService
    }
}
