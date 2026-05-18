package com.flowspeed.link.service

import java.io.File

/**
 * Configuration parsed from CLI arguments.
 */
data class ServiceConfig(
    /** Port for browser extension HTTP server (default: 15151) */
    val integrationPort: Int = 15151,
    /** Port for IPC server (UI communication, default: 15152) */
    val ipcPort: Int = 15152,
    /** Root directory for all persistent data */
    val dataDir: File = getDefaultDataDir(),
    /** Run in background mode (no console output) */
    val background: Boolean = false,
) {
    companion object {
        fun parse(args: Array<String>): ServiceConfig {
            var integrationPort = 15151
            var ipcPort = 15152
            var dataDir: File? = null
            var background = false

            val iter = args.iterator()
            while (iter.hasNext()) {
                when (val arg = iter.next()) {
                    "--port" -> integrationPort = iter.next().toInt()
                    "--ipc-port" -> ipcPort = iter.next().toInt()
                    "--data-dir" -> dataDir = File(iter.next())
                    "--background" -> background = true
                    else -> System.err.println("Unknown argument: $arg")
                }
            }

            return ServiceConfig(
                integrationPort = integrationPort,
                ipcPort = ipcPort,
                dataDir = dataDir ?: getDefaultDataDir(),
                background = background,
            )
        }

        private fun getDefaultDataDir(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            return when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Flow")
                os.contains("mac") -> File(home, "Library/Application Support/Flow")
                else -> File(home, ".local/share/Flow")
            }
        }
    }
}
