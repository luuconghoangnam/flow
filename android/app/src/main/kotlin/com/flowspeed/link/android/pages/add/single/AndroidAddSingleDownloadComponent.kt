package com.flowspeed.link.android.pages.add.single

import com.flowspeed.link.shared.action.createNewQueueAction
import com.flowspeed.link.shared.downloaderinui.DownloaderInUi
import com.flowspeed.link.shared.pagemanager.CategoryDialogManager
import com.flowspeed.link.shared.pagemanager.NewQueuePageManager
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
import com.flowspeed.lib.util.flow.mapStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer

class AndroidAddSingleDownloadComponent(
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
), CategoryDialogManager, NewQueuePageManager {
    val categoryComponentNavigation = SlotNavigation<Long>()
    val categorySlot = childSlot(
        source = categoryComponentNavigation,
        childFactory = { config, ctx ->
            CategoryComponent(
                ctx = ctx,
                id = config,
                close = ::closeCategoryDialog,
                submit = { submittedCategory ->
                    if (submittedCategory.id < 0) {
                        categoryManager.addCustomCategory(submittedCategory)
                    } else {
                        categoryManager.updateCategory(
                            submittedCategory.id
                        ) {
                            submittedCategory.copy(
                                items = it.items
                            )
                        }
                    }
                    closeCategoryDialog()
                },
            )
        },
        serializer = Long.serializer(),
    ).subscribeAsStateFlow()
    val newQueuesAction = createNewQueueAction(
        appScope,
        this,
    )

    override fun openCategoryDialog(categoryId: Long) {
        scope.launch {
            categoryComponentNavigation.activate(categoryId)
        }
    }

    override fun closeCategoryDialog() {
        scope.launch {
            categoryComponentNavigation.dismiss()
        }
    }

    override fun getCategoryPageManager(): CategoryDialogManager {
        return this
    }

    private val _showMoreInputs = MutableStateFlow(false)
    val showMoreInputs = _showMoreInputs.asStateFlow()
    fun setShowMoreInputs(value: Boolean) {
        _showMoreInputs.value = value
    }

    private val _showAddQueue = MutableStateFlow(false)
    val showAddQueue = _showAddQueue.asStateFlow()
    fun setShowAddQueue(value: Boolean) {
        _showAddQueue.value = value
    }

    val isWebPage = downloadChecker
        .responseInfo
        .mapStateFlow { it?.isWebPage ?: false }

    fun createQueueWithName(name: String) {
        scope.launch { queueManager.addQueue(name) }
        setShowAddQueue(false)
    }

    override fun closeNewQueueDialog() {
        setShowAddQueue(false)
    }

    override fun openNewQueueDialog() {
        setShowAddQueue(true)
    }

    fun onRequestOpenLinkInBrowser() {
        sendEffect(
            Effects.OpenInBrowser(
                downloadChecker.credentials.value.link
            )
        )
    }

    sealed interface Effects : BaseAddSingleDownloadComponent.Effects.Platform {
        data class OpenInBrowser(val link: String) : Effects
    }
}
