package com.flowspeed.link.desktop.utils

import com.flowspeed.lib.util.platform.Platform
import com.flowspeed.lib.util.platform.isLinux
import com.flowspeed.lib.util.platform.isMac
import java.io.File

object AutoStartManager {

    fun setupAutoStart() {
        runCatching {
            val execFile = AppInfo.exeFile ?: return
            
            if (Platform.isLinux()) {
                setupLinuxAutoStart(execFile)
            } else if (Platform.isMac()) {
                setupMacAutoStart(execFile)
            }
        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun setupLinuxAutoStart(execFile: String) {
        val homePath = System.getProperty("user.home")
        val autostartDir = File(homePath, ".config/autostart")
        if (!autostartDir.exists()) {
            autostartDir.mkdirs()
        }

        val desktopEntryFilename = AppInfo.packageName
        val desktopEntryFile = File(autostartDir, "${desktopEntryFilename}.desktop")
        
        // Always overwrite to ensure correct path
        val desktopEntryContent = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Name=${AppInfo.displayName}")
            appendLine("Comment=Flow Download Manager")
            appendLine("Exec=\"$execFile\" --background")
            appendLine("Terminal=false")
            appendLine("Type=Application")
            appendLine("X-GNOME-Autostart-enabled=true")
        }
        
        desktopEntryFile.writeText(desktopEntryContent)
    }

    private fun setupMacAutoStart(execFile: String) {
        val homePath = System.getProperty("user.home")
        val launchAgentsDir = File(homePath, "Library/LaunchAgents")
        if (!launchAgentsDir.exists()) {
            launchAgentsDir.mkdirs()
        }

        val plistFilename = "${AppInfo.packageName}.plist"
        val plistFile = File(launchAgentsDir, plistFilename)

        val plistContent = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
            appendLine("<plist version=\"1.0\">")
            appendLine("<dict>")
            appendLine("    <key>Label</key>")
            appendLine("    <string>${AppInfo.packageName}</string>")
            appendLine("    <key>ProgramArguments</key>")
            appendLine("    <array>")
            appendLine("        <string>$execFile</string>")
            appendLine("        <string>--background</string>")
            appendLine("    </array>")
            appendLine("    <key>RunAtLoad</key>")
            appendLine("    <true/>")
            appendLine("</dict>")
            appendLine("</plist>")
        }

        plistFile.writeText(plistContent)
    }
}
