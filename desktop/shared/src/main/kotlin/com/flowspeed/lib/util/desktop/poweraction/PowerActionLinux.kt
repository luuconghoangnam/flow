package com.flowspeed.lib.util.desktop.poweraction

import com.flowspeed.lib.util.execAndWait

class PowerActionLinux : PowerAction {
    override fun initiate(config: PowerActionConfig): Boolean {
        return when (config.type) {
            PowerActionConfig.Type.Shutdown -> shutdown(config.force)
            PowerActionConfig.Type.Hibernate -> hibernate()
            PowerActionConfig.Type.Sleep -> sleep()
        }
    }

    private fun shutdown(force: Boolean): Boolean {
        val commands = listOf(
            arrayOf(
                "dbus-send", "--system", "--print-reply",
                "--dest=org.freedesktop.login1",
                "/org/freedesktop/login1",
                "org.freedesktop.login1.Manager.PowerOff",
                "boolean:true",
            ),
            arrayOf(
                "systemctl", "poweroff"
            ),
        )
        return commands.any { command ->
            runCatching {
                execAndWait(command)
            }.getOrElse { false }
        }
    }

    private fun hibernate(): Boolean {
        return listOf(
            arrayOf("systemctl", "hibernate"),
            arrayOf("loginctl", "hibernate"),
        ).any(::execAndWait)
    }

    private fun sleep(): Boolean {
        return listOf(
            arrayOf("systemctl", "suspend"),
            arrayOf("loginctl", "suspend"),
        ).any(::execAndWait)
    }
}
