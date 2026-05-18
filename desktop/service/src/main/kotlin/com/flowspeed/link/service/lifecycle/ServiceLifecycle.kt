package com.flowspeed.link.service.lifecycle

import com.flowspeed.link.service.ServiceConfig
import com.flowspeed.link.service.ServiceDi
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

/**
 * Manages the service boot sequence and shutdown.
 *
 * Boot order:
 * 1. Initialize DI (lazy, no heavy work yet)
 * 2. Boot folders registry (create directories)
 * 3. Boot download system (load pending downloads)
 * 4. Start Integration HTTP server (browser extension)
 * 5. Start IPC server (UI communication)
 * 6. Write IPC port to lock file
 * 7. Signal ready
 *
 * Shutdown:
 * 1. Stop accepting new requests
 * 2. Pause active downloads (persist progress)
 * 3. Stop servers
 * 4. Release lock
 */
class ServiceLifecycle(
    private val config: ServiceConfig,
    private val lock: SingleInstanceLock,
) {
    private val shutdownLatch = CountDownLatch(1)
    private lateinit var di: ServiceDi

    @Volatile
    private var ready = false

    fun boot() {
        di = ServiceDi(config)

        runBlocking {
            // 1. Create data directories
            di.foldersRegistry.boot()

            // 2. Boot download system
            di.queueManager.boot()
            di.downloadManager.boot()
            di.manualDownloadQueue.boot()
        }

        // 3. Start Integration server (browser extension)
        // TODO: Start integration server on config.integrationPort

        // 4. Start IPC server
        // TODO: Start IPC server on config.ipcPort

        // 5. Write port to lock file
        lock.writePort(config.ipcPort)

        // 6. Ready
        ready = true
        if (!config.background) {
            println("Flow service started (integration: ${config.integrationPort}, ipc: ${config.ipcPort})")
        }
    }

    /** Blocks until shutdown is requested. */
    fun awaitShutdown() {
        shutdownLatch.await()
    }

    /** Initiates graceful shutdown. */
    fun shutdown() {
        if (!config.background) {
            println("Shutting down...")
        }
        runBlocking {
            // Stop active downloads gracefully
            di.downloadManager.stopAll()
        }
        // TODO: Stop IPC server
        // TODO: Stop Integration server
        lock.release()
        shutdownLatch.countDown()
    }

    fun isReady(): Boolean = ready

    fun requestShutdown() {
        Thread { shutdown() }.start()
    }
}
