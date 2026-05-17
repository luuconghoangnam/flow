package com.flowspeed.lib.util.desktop.poweraction

interface PowerAction {
    fun initiate(config: PowerActionConfig): Boolean
}
