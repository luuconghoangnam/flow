package com.flowspeed.link.service.lifecycle

import com.flowspeed.link.service.ServiceConfig
import com.flowspeed.link.service.ServiceDi
import com.flowspeed.link.service.ipc.IpcServer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

/**
 * Manages the service boot sequence and shutdown.
 */
class ServiceLifecycle(
    private val config: ServiceConfig,
    private val lock: SingleInstanceLock,
) {
    private val shutdownLatch = CountDownLatch(1)
    private lateinit var di: ServiceDi
    private var ipcServer: IpcServer? = null

    @Volatile
    private var ready = false

    fun boot() {
        di = ServiceDi(config)

        // Migrate legacy data if needed (first startup after upgrade)
        DataMigration.migrateIfNeeded(config.dataDir)

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
        ipcServer = IpcServer(
            port = config.ipcPort,
            di = di,
            json = di.json,
            onShutdownRequested = ::requestShutdown,
        ).also { it.start() }

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
            di.downloadManager.stopAll()
        }
        ipcServer?.stop()
        ipcServer = null
        lock.release()
        shutdownLatch.countDown()
    }

    fun isReady(): Boolean = ready

    fun requestShutdown() {
        Thread { shutdown() }.start()
    }
}
