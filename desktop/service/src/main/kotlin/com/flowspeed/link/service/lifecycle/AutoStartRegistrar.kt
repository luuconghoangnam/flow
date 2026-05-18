package com.flowspeed.link.service.lifecycle

import java.io.File

/**
 * Registers/unregisters the service for auto-start on system boot.
 * Platform-specific implementations for Windows, Linux, and macOS.
 */
object AutoStartRegistrar {

    private val os = System.getProperty("os.name").lowercase()

    /**
     * Registers the service to start automatically on user login.
     * @param serviceBinaryPath Absolute path to the service executable.
     */
    fun register(serviceBinaryPath: String) {
        when {
            os.contains("win") -> registerWindows(serviceBinaryPath)
            os.contains("mac") -> registerMacOS(serviceBinaryPath)
            else -> registerLinux(serviceBinaryPath)
        }
    }

    /** Removes auto-start registration. */
    fun unregister() {
        when {
            os.contains("win") -> unregisterWindows()
            os.contains("mac") -> unregisterMacOS()
            else -> unregisterLinux()
        }
    }

    /** Checks if auto-start is currently registered. */
    fun isRegistered(): Boolean {
        return when {
            os.contains("win") -> isRegisteredWindows()
            os.contains("mac") -> isRegisteredMacOS()
            else -> isRegisteredLinux()
        }
    }

    // --- Windows: Startup folder shortcut ---

    private val windowsStartupDir: File
        get() = File(System.getenv("APPDATA"), "Microsoft/Windows/Start Menu/Programs/Startup")

    private val windowsShortcutFile: File
        get() = File(windowsStartupDir, "FlowService.vbs")

    private fun registerWindows(serviceBinaryPath: String) {
        // Use a VBS script to launch silently (no console window)
        val quote = "\""
        val vbsContent = buildString {
            appendLine("Set WshShell = CreateObject(${quote}WScript.Shell${quote})")
            appendLine("WshShell.Run ${quote}${quote}${quote}${quote} & ${quote}${serviceBinaryPath}${quote} & ${quote}${quote}${quote}${quote} & ${quote} --background${quote}, 0, False")
        }
        windowsStartupDir.mkdirs()
        windowsShortcutFile.writeText(vbsContent)
    }

    private fun unregisterWindows() {
        windowsShortcutFile.delete()
    }

    private fun isRegisteredWindows(): Boolean {
        return windowsShortcutFile.exists()
    }

    // --- macOS: LaunchAgent plist ---

    private val macPlistFile: File
        get() = File(System.getProperty("user.home"), "Library/LaunchAgents/link.flowspeed.service.plist")

    private fun registerMacOS(serviceBinaryPath: String) {
        val plist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>link.flowspeed.service</string>
                <key>ProgramArguments</key>
                <array>
                    <string>$serviceBinaryPath</string>
                    <string>--background</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <true/>
                <key>StandardOutPath</key>
                <string>/tmp/flow-service.log</string>
                <key>StandardErrorPath</key>
                <string>/tmp/flow-service.err</string>
            </dict>
            </plist>
        """.trimIndent()
        macPlistFile.parentFile.mkdirs()
        macPlistFile.writeText(plist)
        // Load the agent
        runCommand("launchctl", "load", macPlistFile.absolutePath)
    }

    private fun unregisterMacOS() {
        if (macPlistFile.exists()) {
            runCommand("launchctl", "unload", macPlistFile.absolutePath)
            macPlistFile.delete()
        }
    }

    private fun isRegisteredMacOS(): Boolean {
        return macPlistFile.exists()
    }

    // --- Linux: XDG autostart desktop entry ---

    private val linuxAutostartDir: File
        get() = File(System.getProperty("user.home"), ".config/autostart")

    private val linuxDesktopFile: File
        get() = File(linuxAutostartDir, "flow-service.desktop")

    private fun registerLinux(serviceBinaryPath: String) {
        val desktopEntry = """
            [Desktop Entry]
            Type=Application
            Name=Flow Download Service
            Exec=$serviceBinaryPath --background
            Hidden=false
            NoDisplay=true
            X-GNOME-Autostart-enabled=true
            Comment=Flow Download Manager background service
        """.trimIndent()
        linuxAutostartDir.mkdirs()
        linuxDesktopFile.writeText(desktopEntry)
    }

    private fun unregisterLinux() {
        linuxDesktopFile.delete()
    }

    private fun isRegisteredLinux(): Boolean {
        return linuxDesktopFile.exists()
    }

    // --- Utility ---

    private fun runCommand(vararg args: String) {
        try {
            ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Exception) {
            // Best effort - don't crash if command fails
        }
    }
}
