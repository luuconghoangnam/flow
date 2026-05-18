package com.flowspeed.lib.util.desktop.activator.mac

import com.flowspeed.lib.util.desktop.PlatformAppActivator
import com.flowspeed.lib.util.desktop.utils.mac.FoundationLibrary

class MacAppActivator : PlatformAppActivator {
    override fun active() {
        val requiredFoundation = FoundationLibrary.INSTANCE ?: return
        runCatching {
            val nsAppClass = requiredFoundation.objc_getClass("NSApplication")
            val sharedAppSel = requiredFoundation.sel_registerName("sharedApplication")
            val activateSel = requiredFoundation.sel_registerName("activateIgnoringOtherApps:")

            val nsApp = requiredFoundation.objc_msgSend(nsAppClass, sharedAppSel)
            requiredFoundation.objc_msgSend(nsApp, activateSel, true)
        }
    }
}