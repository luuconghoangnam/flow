package com.flowspeed.lib.util.desktop.poweraction

import com.flowspeed.lib.util.execAndWait

class PowerActionMac : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown(config.force)
            PowerActionConfig.Type.Hibernate -> sleep()
            PowerActionConfig.Type.Sleep -> sleep()
        }
    }

    private fun shutdown(force: Boolean): Boolean {
        return execAndWait(
            arrayOf(
                "osascript", "-e", "tell application \"System Events\" to shut down"
            )
        )
    }

    private fun sleep(): Boolean {
        return execAndWait(arrayOf("pmset", "sleepnow"))
    }
}
