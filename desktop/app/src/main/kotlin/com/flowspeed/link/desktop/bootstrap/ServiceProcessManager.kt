package com.flowspeed.link.desktop.bootstrap

import com.flowspeed.link.desktop.utils.AppInfo
import java.io.File

/**
 * Manages the Go background service process lifecycle.
 *
 * Hybrid approach:
 * - When UI starts: launch service if not already running
 * - When UI exits to tray: service keeps running (handles extension requests)
 * - When user clicks "Exit": stop service too
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
        if (isRunning()) return

        val binary = findServiceBinary()
        if (binary == null) {
            println("[ServiceProcessManager] Service binary not found, skipping")
            return
        }

        try {
            val pb = ProcessBuilder(
                binary.absolutePath,
                "--background",
                "--port", "15151",
                "--ipc-port", "15152",
            )
            pb.redirectErrorStream(true)
            // Don't inherit IO - service runs silently
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            serviceProcess = pb.start()
            println("[ServiceProcessManager] Started service (PID: ${serviceProcess?.pid()})")
        } catch (e: Exception) {
            System.err.println("[ServiceProcessManager] Failed to start service: ${e.message}")
        }
    }

    /** Checks if the service process is still alive. */
    fun isRunning(): Boolean {
        return serviceProcess?.isAlive == true
    }

    /** Stops the service process gracefully. */
    fun stop() {
        serviceProcess?.let { proc ->
            if (proc.isAlive) {
                // Try graceful shutdown via IPC first
                try {
                    val url = java.net.URI("http://127.0.0.1:15152/api/shutdown")
                    val conn = url.toURL().openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.responseCode // trigger request
                    conn.disconnect()
                    // Wait up to 3 seconds for graceful exit
                    proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) {
                    // Graceful failed, force kill
                }
                if (proc.isAlive) {
                    proc.destroyForcibly()
                }
                println("[ServiceProcessManager] Service stopped")
            }
        }
        serviceProcess = null
    }
}
