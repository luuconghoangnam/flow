package com.flowspeed.link.desktop.ui

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

/**
 * Lightweight AWT-based system tray that runs without Compose/Skia.
 *
 * This is used when the app is running in background (no window visible).
 * It consumes ~0 extra memory since AWT is already loaded by the JVM.
 * When the user clicks the tray icon, we launch the full Compose UI.
 *
 * This replaces the Compose-based Tray when no windows are open,
 * allowing the Compose runtime + Skia to be fully disposed.
 */
class LightweightTray(
    private val tooltip: String,
    private val onShowWindow: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onExit: () -> Unit,
) {
    private var trayIcon: TrayIcon? = null

    fun show() {
        if (!SystemTray.isSupported()) return
        if (trayIcon != null) return // already showing

        val image = loadTrayImage()
        val popup = createPopupMenu()

        trayIcon = TrayIcon(image, tooltip, popup).apply {
            isImageAutoSize = true
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        onShowWindow()
                    }
                }
            })
        }

        try {
            SystemTray.getSystemTray().add(trayIcon)
        } catch (e: AWTException) {
            System.err.println("Failed to add tray icon: ${e.message}")
        }
    }

    fun hide() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
        trayIcon = null
    }

    // ---------------------------------------------------------------------------
    // Notifications — native OS toast without Compose
    // ---------------------------------------------------------------------------

    /**
     * Shows a native OS notification via AWT TrayIcon.
     * - Windows: balloon notification
     * - Linux: libnotify popup (most DEs support this)
     * - macOS: falls back to osascript (AWT displayMessage deprecated on macOS)
     */
    fun displayMessage(title: String, text: String, type: TrayIcon.MessageType = TrayIcon.MessageType.INFO) {
        val icon = trayIcon
        if (icon != null) {
            try {
                icon.displayMessage(title, text, type)
            } catch (_: Exception) {
                // Fallback for macOS or unsupported platforms
                macOSNotifyFallback(title, text)
            }
        } else {
            // No tray icon available — try macOS native notification
            macOSNotifyFallback(title, text)
        }
    }

    private fun macOSNotifyFallback(title: String, text: String) {
        try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("mac")) {
                val escapedTitle = title.replace("\"", "\\\"")
                val escapedText = text.replace("\"", "\\\"")
                Runtime.getRuntime().exec(
                    arrayOf(
                        "osascript", "-e",
                        """display notification "$escapedText" with title "$escapedTitle""""
                    )
                )
            }
        } catch (_: Exception) {
            // Silent fail — notification is best-effort
        }
    }

    private fun createPopupMenu(): PopupMenu {
        return PopupMenu().apply {
            add(MenuItem("Show Downloads").apply {
                addActionListener { onShowWindow() }
            })
            add(MenuItem("Settings").apply {
                addActionListener { onOpenSettings() }
            })
            addSeparator()
            add(MenuItem("Exit").apply {
                addActionListener { onExit() }
            })
        }
    }

    private fun loadTrayImage(): Image {
        // Try to load from resources, fallback to a simple generated icon
        return try {
            val stream = javaClass.classLoader.getResourceAsStream("com/flowspeed/link/resources/app_icon.png")
            if (stream != null) {
                ImageIO.read(stream)
            } else {
                createFallbackIcon()
            }
        } catch (_: Exception) {
            createFallbackIcon()
        }
    }

    private fun createFallbackIcon(): Image {
        // Create a simple 16x16 icon as fallback
        val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(0x4CAF50)
        g.fillOval(2, 2, 12, 12)
        g.dispose()
        return img
    }
}
