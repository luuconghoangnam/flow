package com.flowspeed.link.desktop.utils

import com.flowspeed.link.shared.util.DownloadSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Manages memory usage by triggering GC and trimming resources when the app is idle.
 * When no downloads are active and no UI is shown, aggressively reclaims memory
 * to keep the background footprint minimal (similar to IDM behavior).
 *
 * Strategy:
 * - Evict idle OkHttp connections (each holds socket buffers ~64KB)
 * - Force GC to release unreachable objects
 * - Periodic trim every 60s when idle to catch any leaked memory
 */
class MemoryManager(
    private val downloadSystem: DownloadSystem,
    private val scope: CoroutineScope,
) : KoinComponent {
    private val okHttpClient: OkHttpClient by inject()
    private var idleJob: Job? = null
    private var periodicJob: Job? = null

    /**
     * Tracks whether the UI window is currently visible.
     * When false and no downloads active, we aggressively reclaim memory.
     */
    private val _isUiVisible = MutableStateFlow(false)

    fun setUiVisible(visible: Boolean) {
        _isUiVisible.value = visible
    }

    fun boot() {
        idleJob?.cancel()
        periodicJob?.cancel()

        idleJob = scope.launch {
            combine(
                _isUiVisible,
                downloadSystem.downloadMonitor.activeDownloadCount,
            ) { uiVisible, activeCount ->
                !uiVisible && activeCount == 0
            }
                .distinctUntilChanged()
                .debounce(5_000) // Wait 5 seconds of idle before reclaiming
                .collect { isIdle ->
                    if (isIdle) {
                        trimMemory()
                    }
                }
        }

        // Periodic memory trim when idle (every 60 seconds)
        periodicJob = scope.launch {
            while (true) {
                delay(60_000)
                val isIdle = !_isUiVisible.value &&
                        downloadSystem.downloadMonitor.activeDownloadCount.value == 0
                if (isIdle) {
                    trimMemory()
                }
            }
        }
    }

    /**
     * Aggressively reclaim memory when app is idle in background.
     * This brings RAM usage down significantly when no work is being done.
     */
    private fun trimMemory() {
        // Evict all idle connections from OkHttp pool
        // Each idle connection holds ~64KB of socket buffers
        okHttpClient.connectionPool.evictAll()

        // Hint the JVM to release unused memory back to the OS
        System.gc()
        System.runFinalization()
        System.gc()
    }

    fun stop() {
        idleJob?.cancel()
        periodicJob?.cancel()
        idleJob = null
        periodicJob = null
    }
}
