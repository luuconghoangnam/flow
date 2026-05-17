package com.flowspeed.link.desktop.pages.addDownload.single

import com.flowspeed.link.shared.action.createNewQueueAction
import com.flowspeed.link.shared.downloaderinui.DownloaderInUi
import com.flowspeed.link.shared.pagemanager.CategoryDialogManager
import com.flowspeed.link.shared.pagemanager.QueuePageManager
import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.link.shared.pages.adddownload.ImportOptions
import com.flowspeed.link.shared.pages.adddownload.single.BaseAddSingleDownloadComponent
import com.flowspeed.link.shared.pages.adddownload.single.OnRequestAddSingleItem
import com.flowspeed.link.shared.pages.adddownload.single.OnRequestDownloadSingleItem
import com.flowspeed.link.shared.pages.category.CategoryComponent
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ILastSavedLocationsStorage
import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.FileIconProvider
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.flowspeed.link.shared.util.subscribeAsStateFlow
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.queue.QueueManager
import kotlinx.coroutines.CoroutineScope

class DesktopAddSingleDownloadComponent(
    ctx: ComponentContext,
    onRequestClose: () -> Unit,
    onRequestDownload: OnRequestDownloadSingleItem,
    onRequestAddToQueue: OnRequestAddSingleItem,
    openExistingDownload: (Long) -> Unit,
    updateExistingDownloadCredentials: (Long, IDownloadCredentials, DownloadJobExtraConfig?) -> Unit,
    downloadItemOpener: DownloadItemOpener,
    lastSavedLocationsStorage: ILastSavedLocationsStorage,
    queueManager: QueueManager,
    categoryManager: CategoryManager,
    downloadSystem: DownloadSystem,
    appSettings: BaseAppSettingsStorage,
    iconProvider: FileIconProvider,
    appScope: CoroutineScope,
    appRepository: BaseAppRepository,
    perHostSettingsManager: PerHostSettingsManager,
    importOptions: ImportOptions,
    id: String,
    downloaderInUi: DownloaderInUi<IDownloadCredentials, *, *, *, *, *, *, *, *, *>,
    initialCredentials: AddDownloadCredentialsInUiProps,
    private val categoryDialogManager: CategoryDialogManager,
) : BaseAddSingleDownloadComponent(
    ctx = ctx,
    onRequestClose = onRequestClose,
    onRequestDownload = onRequestDownload,
    onRequestAddToQueue = onRequestAddToQueue,
    openExistingDownload = openExistingDownload,
    updateExistingDownloadCredentials = updateExistingDownloadCredentials,
    downloadItemOpener = downloadItemOpener,
    lastSavedLocationsStorage = lastSavedLocationsStorage,
    importOptions = importOptions,
    id = id,
    downloaderInUi = downloaderInUi,
    initialCredentials = initialCredentials,
    queueManager = queueManager,
    categoryManager = categoryManager,
    downloadSystem = downloadSystem,
    appSettings = appSettings,
    iconProvider = iconProvider,
    appScope = appScope,
    appRepository = appRepository,
    perHostSettingsManager = perHostSettingsManager,
) {
    override fun getCategoryPageManager(): CategoryDialogManager {
        return categoryDialogManager
    }
}
