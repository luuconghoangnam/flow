package com.flowspeed.lib.util.desktop.poweraction

import com.flowspeed.lib.util.execAndWait

class PowerActionWindows : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown(config.force)
            PowerActionConfig.Type.Hibernate -> hibernate()
            PowerActionConfig.Type.Sleep -> sleep()
        }
    }

    private fun shutdown(force: Boolean): Boolean {
        val command = arrayOf("shutdown", "/s", "/t", "0")
        return execAndWait(command)
    }

    private fun hibernate(): Boolean {
        return execAndWait(arrayOf("shutdown", "/h"))
    }

    private fun sleep(): Boolean {
        return execAndWait(arrayOf("rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"))
    }
}
