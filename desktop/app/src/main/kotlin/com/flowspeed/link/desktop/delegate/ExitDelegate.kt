package com.flowspeed.link.desktop.delegate

import com.flowspeed.link.desktop.bootstrap.ServiceProcessManager
import com.flowspeed.link.shared.pagemanager.ExitApplicationRequestManager
import com.flowspeed.link.shared.util.DownloadSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Handles application exit logic including confirmation
 * when downloads are active.
 */
class ExitDelegate(
    private val scope: CoroutineScope,
    private val downloadSystem: DownloadSystem,
) : ExitApplicationRequestManager {

    private val _showConfirmExitDialog = MutableStateFlow(false)
    val showConfirmExitDialog = _showConfirmExitDialog.asStateFlow()

    override suspend fun requestExitApp() {
        val hasActiveDownloads = downloadSystem.downloadMonitor.activeDownloadCount.value > 0
        if (hasActiveDownloads) {
            _showConfirmExitDialog.value = true
            return
        }
        exitApp()
    }

    fun exitAppAsync() {
        scope.launch { exitApp() }
    }

    suspend fun exitApp() {
        downloadSystem.stopAnything()
        // Stop the Go background service when user exits completely
        ServiceProcessManager.stop()
        exitProcess(0)
    }

    fun closeConfirmExit() {
        _showConfirmExitDialog.value = false
    }
}
