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
 */
class MemoryManager(
    private val downloadSystem: DownloadSystem,
    private val scope: CoroutineScope,
) : KoinComponent {
    private val okHttpClient: OkHttpClient by inject()
    private var job: Job? = null

    /**
     * Tracks whether the UI window is currently visible.
     * When false and no downloads active, we aggressively reclaim memory.
     */
    private val _isUiVisible = MutableStateFlow(false)

    fun setUiVisible(visible: Boolean) {
        _isUiVisible.value = visible
    }

    fun boot() {
        job?.cancel()
        job = scope.launch {
            // Combine UI visibility and active download count
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
        scope.launch {
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
        okHttpClient.connectionPool.evictAll()
        // Hint the JVM to release unused memory back to the OS
        System.gc()
        // Second pass to collect objects finalized in first pass
        System.runFinalization()
        System.gc()
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
