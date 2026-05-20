package com.flowspeed.lib.util.desktop.activator.win

import com.flowspeed.lib.util.desktop.PlatformAppActivator
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

class WindowsAppActivator : PlatformAppActivator {
    override fun active() {
        val currentPid = Kernel32.INSTANCE.GetCurrentProcessId()
        User32.INSTANCE.EnumWindows(WinUser.WNDENUMPROC { hwnd, _ ->
            val pidRef = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
            if (pidRef.value == currentPid) {
                if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                    bringToFront(hwnd)
                }
            }
            true
        }, null)
    }

    private fun bringToFront(hwnd: HWND) {
        val foregroundHwnd = User32.INSTANCE.GetForegroundWindow()
        if (foregroundHwnd == hwnd) return

        val foregroundThreadId = User32.INSTANCE.GetWindowThreadProcessId(foregroundHwnd, null)
        val currentThreadId = Kernel32.INSTANCE.GetCurrentThreadId()

        if (foregroundThreadId != currentThreadId) {
            User32.INSTANCE.AttachThreadInput(
                WinDef.DWORD(currentThreadId.toLong()),
                WinDef.DWORD(foregroundThreadId.toLong()),
                true
            )
            User32.INSTANCE.ShowWindow(hwnd, User32.SW_SHOW)
            User32.INSTANCE.SetForegroundWindow(hwnd)
            User32.INSTANCE.SetFocus(hwnd)
            User32.INSTANCE.AttachThreadInput(
                WinDef.DWORD(currentThreadId.toLong()),
                WinDef.DWORD(foregroundThreadId.toLong()),
                false
            )
        } else {
            User32.INSTANCE.ShowWindow(hwnd, User32.SW_SHOW)
            User32.INSTANCE.SetForegroundWindow(hwnd)
            User32.INSTANCE.SetFocus(hwnd)
        }
    }
}
