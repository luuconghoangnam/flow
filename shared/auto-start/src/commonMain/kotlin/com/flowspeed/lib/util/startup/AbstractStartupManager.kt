package com.flowspeed.lib.util.startup

abstract class AbstractStartupManager {
    @Throws(Exception::class)
    abstract fun install()
    abstract fun uninstall()
}
