package com.flowspeed.link.desktop.bootstrap

import com.flowspeed.link.desktop.utils.AppInfo
import java.io.File

/**
 * Manages the Go background service process lifecycle.
 *
 * Hybrid approach:
 * - When UI starts: launch service if not already running
 * - When UI exits: service keeps running (handles extension requests via overlay)
 * - When user clicks "Exit" in tray: service stops too
 *
 * The service binary (flow-service.exe) is expected to be in the same
 * directory as the main app executable.
 */
object ServiceProcessManager {
    private var serviceProcess: Process? = null

    /** Finds the service binary next to the app executable. */
    private fun findServiceBinary(): File? {
        // Try next to the app exe
        val appDir = AppInfo.installationFolder
        if (appDir != null) {
            val binary = File(appDir, getServiceBinaryName())
            if (binary.exists()) return binary
        }
        // Try in current working directory
        val cwd = File(getServiceBinaryName())
        if (cwd.exists()) return cwd
        // Try in parent directory
        val parent = File("..", getServiceBinaryName())
        if (parent.exists()) return parent
        return null
    }

    private fun getServiceBinaryName(): String {
        return if (System.getProperty("os.name").lowercase().contains("win")) {
            "flow-service.exe"
        } else {
            "flow-service"
        }
    }

    /**
     * Starts the Go service process if not already running.
     * The service handles browser extension requests on port 15151.
     */
    fun ensureRunning() {
        // If service is already reachable (started by auto-start), don't start another
        if (isServiceReachable()) {
            println("[ServiceProcessManager] Service already running (external)")
            return
        }
        if (isRunning()) return

        val binary = findServiceBinary()
        if (binary == null) {
            println("[ServiceProcessManager] Service binary not found, skipping")
            return
        }

        // Register auto-start if not already registered
        registerAutoStart(binary)

        try {
            val pb = ProcessBuilder(
                binary.absolutePath,
                "--background",
                "--port", "15151",
                "--ipc-port", "15152",
            )
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            serviceProcess = pb.start()
            println("[ServiceProcessManager] Started service (PID: ${serviceProcess?.pid()})")
            // Give service time to start
            Thread.sleep(500)
        } catch (e: Exception) {
            System.err.println("[ServiceProcessManager] Failed to start service: ${e.message}")
        }
    }

    /** Registers the service for auto-start on login (first run). */
    private fun registerAutoStart(binary: File) {
        try {
            com.flowspeed.link.service.lifecycle.AutoStartRegistrar.register(binary.absolutePath)
        } catch (_: Exception) {
            // Best effort
        }
    }

    /** Checks if the service process (started by us) is still alive. */
    fun isRunning(): Boolean {
        return serviceProcess?.isAlive == true
    }

    /** Stops the service process gracefully. */
    fun stop() {
        disconnectIPC()
        serviceProcess?.let { proc ->
            if (proc.isAlive) {
                try {
                    val url = java.net.URI("http://127.0.0.1:15152/api/shutdown")
                    val conn = url.toURL().openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.responseCode
                    conn.disconnect()
                    proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) {
                    // Graceful failed
                }
                if (proc.isAlive) {
                    proc.destroyForcibly()
                }
                println("[ServiceProcessManager] Service stopped")
            }
        }
        serviceProcess = null
    }

    /**
     * Notifies the Go service that the UI is connected.
     * Extension requests will be forwarded to the UI via IPC instead of showing the overlay.
     */
    fun connectIPC() {
        try {
            val url = java.net.URI("http://127.0.0.1:15152/api/ui/connect")
            val conn = url.toURL().openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode
            conn.disconnect()
            println("[ServiceProcessManager] Connected to service IPC")
        } catch (e: Exception) {
            println("[ServiceProcessManager] Failed to connect IPC: ${e.message}")
        }
    }

    /**
     * Notifies the Go service that the UI is disconnecting.
     * Extension requests will use the overlay dialog again.
     */
    fun disconnectIPC() {
        try {
            val url = java.net.URI("http://127.0.0.1:15152/api/ui/disconnect")
            val conn = url.toURL().openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode
            conn.disconnect()
            println("[ServiceProcessManager] Disconnected from service IPC")
        } catch (_: Exception) {
            // Service might already be gone
        }
    }

    /**
     * Checks if the Go service is already running (independent of this process).
     * Useful to detect if service was started by auto-start or another instance.
     */
    fun isServiceReachable(): Boolean {
        return try {
            val url = java.net.URI("http://127.0.0.1:15152/api/status")
            val conn = url.toURL().openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            val reachable = conn.responseCode == 200
            conn.disconnect()
            reachable
        } catch (_: Exception) {
            false
        }
    }
}
