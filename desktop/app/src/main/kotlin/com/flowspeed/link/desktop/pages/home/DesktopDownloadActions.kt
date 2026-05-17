package com.flowspeed.link.desktop.pages.home

import com.flowspeed.link.shared.pagemanager.EditDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.FileChecksumDialogManager
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.pagemanager.DownloadDialogManager
import com.flowspeed.link.shared.pages.home.AbstractDownloadActions
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.util.compose.action.MenuItem
import com.flowspeed.lib.util.compose.action.buildMenu
import com.flowspeed.lib.util.compose.action.simpleAction
import com.flowspeed.lib.util.compose.asStringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DesktopDownloadActions(
    scope: CoroutineScope,
    downloadSystem: DownloadSystem,
    downloadDialogManager: DownloadDialogManager,
    editDownloadDialogManager: EditDownloadDialogManager,
    fileChecksumDialogManager: FileChecksumDialogManager,
    selections: StateFlow<List<IDownloadItemState>>,
    queueManager: QueueManager,
    categoryManager: CategoryManager,
    openFile: (Long) -> Unit,
    requestDelete: (List<Long>) -> Unit,
    mainItem: StateFlow<Long?>,
    private val openFolder: (Long) -> Unit,
) : AbstractDownloadActions(
    scope = scope,
    downloadSystem = downloadSystem,
    downloadDialogManager = downloadDialogManager,
    editDownloadDialogManager = editDownloadDialogManager,
    fileChecksumDialogManager = fileChecksumDialogManager,
    selections = selections,
    queueManager = queueManager,
    categoryManager = categoryManager,
    openFile = openFile,
    requestDelete = requestDelete,
    mainItem = mainItem,
) {
    val openFolderAction = simpleAction(
        title = Res.string.open_folder.asStringSource(),
        icon = MyIcons.folderOpen,
        onActionPerformed = {
            scope.launch {
                val d = defaultItem.value ?: return@launch
                openFolder(d.id)
            }
        }
    )

    val menu: List<MenuItem> = buildMenu {
        +openFileAction
        +openFolderAction
        +(resumeAction)
        +pauseAction
        separator()
        +(deleteAction)
        +(reDownloadAction)
        separator()
        +moveToQueueItems
        +moveToCategoryAction
        separator()
        subMenu(Res.string.copy.asStringSource(), MyIcons.copy) {
            +(copyDownloadLinkAction)
            +(copyDownloadCredentialsAsCurlAction)
        }
        +editDownloadAction
        +fileChecksumAction
        +(openDownloadDialogAction)
    }
}
