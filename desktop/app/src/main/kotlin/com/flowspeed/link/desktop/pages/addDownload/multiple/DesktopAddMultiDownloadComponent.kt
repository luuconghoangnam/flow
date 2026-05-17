package com.flowspeed.link.desktop.pages.addDownload.multiple

import com.flowspeed.link.shared.ui.widget.table.customtable.TableState
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.desktop.repository.AppRepository
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.pagemanager.CategoryDialogManager
import com.flowspeed.link.shared.pages.adddownload.multiple.BaseAddMultiDownloadComponent
import com.flowspeed.link.shared.pages.adddownload.multiple.OnRequestAdd
import com.flowspeed.link.shared.storage.ILastSavedLocationsStorage
import com.flowspeed.link.shared.util.FileIconProvider
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.downloader.queue.QueueManager

class DesktopAddMultiDownloadComponent(
    ctx: ComponentContext,
    id: String,
    onRequestClose: () -> Unit,
    onRequestAdd: OnRequestAdd,
    private val categoryDialogManager: CategoryDialogManager,
    lastSavedLocationsStorage: ILastSavedLocationsStorage,
    perHostSettingsManager: PerHostSettingsManager, downloadSystem: DownloadSystem,
    fileIconProvider: FileIconProvider,
    appRepository: AppRepository,
    downloaderInUiRegistry: DownloaderInUiRegistry,
    queueManager: QueueManager,
    categoryManager: CategoryManager,
) : BaseAddMultiDownloadComponent(
    ctx = ctx,
    id = id,
    lastSavedLocationsStorage = lastSavedLocationsStorage,
    onRequestAdd = onRequestAdd,
    onRequestClose = onRequestClose,
    perHostSettingsManager = perHostSettingsManager,
    downloadSystem = downloadSystem,
    appRepository = appRepository,
    fileIconProvider = fileIconProvider,
    downloaderInUiRegistry = downloaderInUiRegistry,
    queueManager = queueManager,
    categoryManager = categoryManager,
) {
    override fun getCategoryPageManager(): CategoryDialogManager {
        return categoryDialogManager
    }
    val tableState = TableState(
        cells = AddMultiItemTableCells.all(),
        forceVisibleCells = listOf(
            AddMultiItemTableCells.Check,
            AddMultiItemTableCells.Name,
        )
    )
}

