package com.flowspeed.link.desktop.ipc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Monitors the background service process and restarts it if it crashes.
 *
 * Runs in the UI/tray process. Checks service health periodically
 * and restarts the service binary if it becomes unresponsive.
 *
 * Rate-limited: max 5 restarts in 60 seconds before giving up.
 */
class ServiceProcessMonitor(
    private val ipcClient: IpcClient,
    private val scope: CoroutineScope,
) {
    private val _serviceStatus = MutableStateFlow(ServiceStatus.Unknown)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private var monitorJob: Job? = null
    private var serviceBinaryPath: String? = null
    private var dataDir: File? = null

    // Restart rate limiting
    private val restartTimestamps = mutableListOf<Long>()
    private val maxRestartsInWindow = 5
    private val restartWindowMs = 60_000L

    enum class ServiceStatus {
        Unknown,
        Running,
        Starting,
        Stopped,
        CrashLooping,
    }

    /**
     * Start monitoring the service.
     * @param binaryPath Path to the service executable (for restart).
     * @param serviceDataDir Data directory passed to service on restart.
     */
    fun startMonitoring(binaryPath: String, serviceDataDir: File) {
        serviceBinaryPath = binaryPath
        dataDir = serviceDataDir
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val healthy = ipcClient.checkHealth()
                if (healthy) {
                    _serviceStatus.value = ServiceStatus.Running
                } else {
                    if (_serviceStatus.value == ServiceStatus.Running) {
                        // Was running, now not responding → try restart
                        handleServiceDown()
                    }
                }
                delay(5_000) // Check every 5 seconds
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun handleServiceDown() {
        if (isRateLimited()) {
            _serviceStatus.value = ServiceStatus.CrashLooping
            return
        }

        _serviceStatus.value = ServiceStatus.Starting
        restartService()

        // Wait for service to come up
        delay(3_000)
        val healthy = ipcClient.checkHealth()
        _serviceStatus.value = if (healthy) ServiceStatus.Running else ServiceStatus.Stopped
    }

    private fun restartService() {
        val binary = serviceBinaryPath ?: return
        val dir = dataDir ?: return

        restartTimestamps.add(System.currentTimeMillis())

        try {
            ProcessBuilder(binary, "--background", "--data-dir", dir.absolutePath)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            System.err.println("Failed to restart service: ${e.message}")
        }
    }

    private fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        restartTimestamps.removeAll { now - it > restartWindowMs }
        return restartTimestamps.size >= maxRestartsInWindow
    }
}
