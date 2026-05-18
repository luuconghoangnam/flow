package com.flowspeed.link.desktop.actions

import com.flowspeed.link.desktop.DesktopDownloadDialogManager
import com.flowspeed.link.shared.action.createStopAllAction
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.lib.downloader.queue.DownloadQueue
import com.flowspeed.lib.util.compose.action.AnAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow


fun createDesktopStopAllAction(
    scope: CoroutineScope,
    downloadSystem: DownloadSystem,
    desktopDownloadDialogManager: DesktopDownloadDialogManager,
    activeQueuesFlow: StateFlow<List<DownloadQueue>>
): AnAction {
    return createStopAllAction(
        scope = scope,
        downloadSystem = downloadSystem,
        activeQueuesFlow = activeQueuesFlow,
        extraJobs = {
            val activeDownloadIds = downloadSystem.downloadMonitor.activeDownloadListFlow.value.map { it.id }
            desktopDownloadDialogManager.closeDownloadDialog(activeDownloadIds)
        }
    )
}
