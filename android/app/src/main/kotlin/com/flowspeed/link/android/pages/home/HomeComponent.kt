package com.flowspeed.link.android.pages.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.flowspeed.link.android.action.createOpenBrowserAction
import com.flowspeed.link.android.pages.enterurl.AndroidEnterNewURLComponent
import com.flowspeed.link.android.pages.home.sections.sort.DownloadSortBy
import com.flowspeed.link.android.storage.HomePageStorage
import com.flowspeed.link.android.util.AppInfo
import com.flowspeed.link.android.util.pagemanager.IBrowserPageManager
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.action.createCheckForUpdateAction
import com.flowspeed.link.shared.action.createDownloadFromClipboardAction
import com.flowspeed.link.shared.action.createDummyExceptionAction
import com.flowspeed.link.shared.action.createDummyMessageAction
import com.flowspeed.link.shared.action.createNewDownloadAction
import com.flowspeed.link.shared.action.createOpenAboutPage
import com.flowspeed.link.shared.action.createOpenBatchDownloadAction
import com.flowspeed.link.shared.action.createOpenOpenSourceThirdPartyLibrariesPage
import com.flowspeed.link.shared.action.createOpenSettingsAction
import com.flowspeed.link.shared.action.createPerHostSettingsPage
import com.flowspeed.link.shared.action.createStartQueueGroupAction
import com.flowspeed.link.shared.action.createStopAllAction
import com.flowspeed.link.shared.action.createStopQueueGroupAction
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.pagemanager.AboutPageManager
import com.flowspeed.link.shared.pagemanager.AddDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.BatchDownloadPageManager
import com.flowspeed.link.shared.pagemanager.CategoryDialogManager
import com.flowspeed.link.shared.pagemanager.DownloadDialogManager
import com.flowspeed.link.shared.pagemanager.EditDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.EnterNewURLDialogManager
import com.flowspeed.link.shared.pagemanager.FileChecksumDialogManager
import com.flowspeed.link.shared.pagemanager.NotificationSender
import com.flowspeed.link.shared.pagemanager.OpenSourceLibrariesPageManager
import com.flowspeed.link.shared.pagemanager.PerHostSettingsPageManager
import com.flowspeed.link.shared.pagemanager.QueuePageManager
import com.flowspeed.link.shared.pagemanager.SettingsPageManager
import com.flowspeed.link.shared.pagemanager.TranslatorsPageManager
import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.link.shared.pages.home.BaseHomeComponent
import com.flowspeed.link.shared.pages.home.category.DefinedStatusCategories
import com.flowspeed.link.shared.pages.home.category.DownloadStatusCategoryFilter
import com.flowspeed.link.shared.pages.updater.UpdateComponent
import com.flowspeed.link.shared.ui.widget.sort.Sort
import com.flowspeed.link.shared.ui.widget.sort.sorted
import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.FileIconProvider
import com.flowspeed.link.shared.util.category.Category
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.category.DefaultCategories
import com.flowspeed.link.shared.util.subscribeAsStateFlow
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.flowspeed.lib.SelectionUtil
import com.flowspeed.lib.downloader.db.QueueModel
import com.flowspeed.lib.downloader.downloaditem.DownloadJobStatus
import com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.lib.downloader.queue.DownloadQueue
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.downloader.queue.activeQueuesFlow
import com.flowspeed.lib.util.compose.action.buildMenu
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.collections.plus

class HomeComponent(
    componentContext: ComponentContext,
    downloadItemOpener: DownloadItemOpener,
    downloadDialogManager: DownloadDialogManager,
    editDownloadDialogManager: EditDownloadDialogManager,
    addDownloadDialogManager: AddDownloadDialogManager,
    fileChecksumDialogManager: FileChecksumDialogManager,
    queuePageManager: QueuePageManager,
    categoryDialogManager: CategoryDialogManager,
    notificationSender: NotificationSender,
    downloadSystem: DownloadSystem,
    categoryManager: CategoryManager,
    queueManager: QueueManager,
    openSourceLibrariesPageManager: OpenSourceLibrariesPageManager,
    translatorsPageManager: TranslatorsPageManager,
    settingsPageManager: SettingsPageManager,
    perHostSettingsPageManager: PerHostSettingsPageManager,
    browserPageManager: IBrowserPageManager,
    aboutPageManager: AboutPageManager,
    batchDownloadPageManager: BatchDownloadPageManager,
    defaultCategories: DefaultCategories,
    fileIconProvider: FileIconProvider,
    downloaderInUiRegistry: DownloaderInUiRegistry,
    private val updateComponent: UpdateComponent,
    private val homePageStorage: HomePageStorage,
) : BaseHomeComponent(
    componentContext,
    downloadItemOpener,
    downloadDialogManager,
    editDownloadDialogManager,
    addDownloadDialogManager,
    fileChecksumDialogManager,
    queuePageManager,
    categoryDialogManager,
    notificationSender,
    downloadSystem,
    categoryManager,
    queueManager,
    defaultCategories,
    fileIconProvider,
), EnterNewURLDialogManager {
    private val enterNewLinkNavigation = SlotNavigation<AndroidEnterNewURLComponent.Config>()
    val enterNewLinkSlot = childSlot(
        source = enterNewLinkNavigation,
        serializer = null,
        key = "enterNewLinkSlot",
        childFactory = { configuration, context ->
            AndroidEnterNewURLComponent(
                ctx = context,
                config = configuration,
                downloaderInUiRegistry = downloaderInUiRegistry,
                onCloseRequest = {
                    closeEnterNewURLWindow()
                },
                onRequestFinished = {
                    addDownloadDialogManager.openAddDownloadDialog(
                        links = listOf(AddDownloadCredentialsInUiProps(it))
                    )
                }
            )
        }
    ).subscribeAsStateFlow()

    override fun closeEnterNewURLWindow() {
        scope.launch {
            enterNewLinkNavigation.dismiss()
        }
    }

    override fun openEnterNewURLWindow() {
        scope.launch {
            enterNewLinkNavigation.activate(AndroidEnterNewURLComponent.Config)
        }
    }

    val downloadActions = AndroidDownloadActions(
        scope = scope,
        downloadSystem = downloadSystem,
        downloadDialogManager = downloadDialogManager,
        editDownloadDialogManager = editDownloadDialogManager,
        fileChecksumDialogManager = fileChecksumDialogManager,
        selections = selectionListItems,
        mainItem = selectionList.mapStateFlow {
            if (it.size == 1) it[0]
            else null
        },
        queueManager = queueManager,
        categoryManager = categoryManager,
        openFile = ::openFile,
        requestDelete = ::requestDelete,
        onRequestShareFiles = ::shareFiles,
    )

    private fun shareFiles(finishedDownloads: List<CompletedDownloadItemState>) {
        finishedDownloads.mapNotNull {
            File(it.folder, it.name).takeIf { file -> file.exists() }
        }.takeIf { it.isNotEmpty() }?.let {
            sendEffect(Effects.ShareFiles(it))
        }

    }

    fun onItemClicked(itemState: IDownloadItemState) {
        scope.launch {
            if (itemState is ProcessingDownloadItemState) {
                toggleDownload(itemState)
                return@launch
            }
            downloadItemOpener.openDownloadItem(itemState.id)
        }
    }

    suspend fun toggleDownload(dItem: ProcessingDownloadItemState) {
        when {
            dItem.canBeResumed() -> downloadSystem.userManualResume(dItem.id)
            dItem.canBePaused() -> downloadSystem.manualPause(dItem.id)
        }
    }

    private val _selectedSort = homePageStorage.sortBy
    val selectedSort = _selectedSort.asStateFlow()
    fun setSelectedSort(
        sort: Sort<DownloadSortBy>
    ) {
        if (sort.cell in possibleSorts) {
            _selectedSort.value = sort
        }
    }

    val filterMode = derivedStateOf {
        val queueFilter = filterState.queueFilter
        val statusFilter = filterState.statusFilter
        val categoryFilter = filterState.typeCategoryFilter
        if (queueFilter != null) {
            FilterMode.Queue(queueFilter)
        } else {
            FilterMode.Status(statusFilter, categoryFilter)
        }
    }

    val sortedDownloadList = combine(
        downloadList,
        selectedSort,
        snapshotFlow { filterMode.value },
    ) { downloadList, sortBy, filterMode ->
        when (filterMode) {
            is FilterMode.Status -> {
                sortBy.sorted(downloadList)
            }

            is FilterMode.Queue -> {
                filterMode.queue.queueItems.mapNotNull { id ->
                    downloadList.find { it.id == id }
                }
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun onRequestSelectInside() {
        SelectionUtil.toggleSelectInside(
            selectionList = selectionList.value,
            fullSortedList = sortedDownloadList.value,
            getId = {
                it.id
            }
        )?.let {
            newSelection(it)
        }
    }

    fun onRequestInvertSelection() {
        newSelection(
            SelectionUtil.invertSelection(
                selectionList = selectionList.value,
                all = sortedDownloadList.value,
                getId = { it.id }
            )
        )
    }

    val allStatuseFilters = DefinedStatusCategories.values()
    val currentStatusIndexInList by derivedStateOf {
        allStatuseFilters.indexOf(filterState.statusFilter)
    }

    fun switchToNewStatus(value: Int) {
        filterState.statusFilter = allStatuseFilters[
            value.coerceIn(allStatuseFilters.indices)
        ]
    }

    private val _isShowingSearch: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isShowingSearch = _isShowingSearch.asStateFlow()
    fun setIsShowingSearch(shown: Boolean) {
        if (!shown) {
            filterState.textToSearch = ""
        } else {
            closePopups()
        }
        _isShowingSearch.value = shown
    }

    private val currentActivePopup = MutableStateFlow<HomePopups?>(null)
    fun onOverlayClicked() {
        closePopups()
    }

    fun closePopups() {
        currentActivePopup.value = null
    }

    val isMainMenuShowing = currentActivePopup.mapStateFlow {
        it == HomePopups.MainMenu
    }

    fun setIsMainMenuShowing(value: Boolean) {
        currentActivePopup.value = HomePopups.MainMenu.takeIf { value }
    }

    val isCategoryFilterShowing = currentActivePopup.mapStateFlow {
        it == HomePopups.FilterMenu
    }

    fun setIsCategoryFilterShowing(value: Boolean) {
        currentActivePopup.value = HomePopups.FilterMenu.takeIf { value }
    }

    val isSortMenuShowing = currentActivePopup.mapStateFlow {
        it == HomePopups.SortMenu
    }

    fun setIsSortMenuShowing(value: Boolean) {
        currentActivePopup.value = HomePopups.SortMenu.takeIf { value }
    }

    val isAddMenuShowing = currentActivePopup.mapStateFlow {
        it == HomePopups.AddMenu
    }

    fun setIsAddMenuShowing(value: Boolean) {
        currentActivePopup.value = HomePopups.AddMenu.takeIf { value }
    }


    val activeQueuesFlow = queueManager.activeQueuesFlow()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val mainMenu = buildMenu {
        +createOpenBrowserAction(browserPageManager = browserPageManager)
        separator()
        +createStopAllAction(scope, downloadSystem, {}, activeQueuesFlow)
        separator()
        subMenu(
            title = Res.string.delete.asStringSource(),
            icon = MyIcons.remove
        ) {
            item(Res.string.all_missing_files.asStringSource()) {
                requestDelete(downloadSystem.getListOfDownloadThatMissingFileOrHaveNotProgress().map { it.id })
            }
            item(Res.string.all_finished.asStringSource()) {
                requestDelete(downloadSystem.getFinishedDownloadIds())
            }
            item(Res.string.all_unfinished.asStringSource()) {
                requestDelete(downloadSystem.getUnfinishedDownloadIds())
            }
            item(Res.string.entire_list.asStringSource()) {
                requestDelete(downloadSystem.getAllDownloadIds())
            }
        }
        separator()
        +createStartQueueGroupAction(scope, queueManager)
        +createStopQueueGroupAction(scope, activeQueuesFlow)
        if (AppInfo.isInDebugMode) {
            separator()
            +createDummyMessageAction(notificationSender)
            +createDummyExceptionAction()
        }
        separator()
        +createPerHostSettingsPage(perHostSettingsPageManager = perHostSettingsPageManager)
        +createOpenSettingsAction(settingsPageManager = settingsPageManager)
        separator()
        subMenu(
            Res.string.help.asStringSource(),
            MyIcons.question,
        ) {
            +createOpenOpenSourceThirdPartyLibrariesPage(openSourceLibrariesPageManager = openSourceLibrariesPageManager)
            separator()
            +createCheckForUpdateAction(updateComponent)
            +createOpenAboutPage(aboutPageManager)
        }
    }
    val addMenu = buildMenu {
        +createDownloadFromClipboardAction(addDownloadDialogManager = addDownloadDialogManager)
        +createNewDownloadAction(enterNewURLDialogManager = enterNewURLDialogManager)
        +createOpenBatchDownloadAction(batchDownloadPageManager = batchDownloadPageManager)
    }

    val isOverlayVisible = currentActivePopup.mapStateFlow {
        it != null
    }

    val possibleSorts = listOf(
        DownloadSortBy.DataAdded,
        DownloadSortBy.Name,
        DownloadSortBy.Size,
        DownloadSortBy.Status,
    )

    fun startQueue(id: Long) {
        scope.launch {
            queueManager.getQueue(id).start()
        }
    }

    fun stopQueue(id: Long) {
        scope.launch {
            queueManager.getQueue(id).stop()
        }
    }

    private fun getCurrentDownloadQueue(): DownloadQueue? {
        val queueId = (filterMode.value as? FilterMode.Queue)?.queue?.id ?: return null
        return runCatching { queueManager.getQueue(queueId) }.getOrNull()
    }

    fun reorderQueueItemsUp() {
        val downloadQueue = getCurrentDownloadQueue() ?: return
        val itemsToMove = selectionList.value
        downloadQueue.moveUp(itemsToMove)
        val queueItems = downloadQueue.queueModel.value.queueItems
        val firstItemId = queueItems.firstOrNull { itemsToMove.contains(it) }
        firstItemId?.let {
            scope.launch {
                sendEffect(BaseHomeComponent.Effects.Common.ScrollToDownloadItem(it, true))
            }
        }
    }

    fun reorderQueueItemsDown() {
        val downloadQueue = getCurrentDownloadQueue() ?: return
        val itemsToMove = selectionList.value
        downloadQueue.moveDown(itemsToMove)
        val queueItems = downloadQueue.queueModel.value.queueItems
        val lastItemId = queueItems.lastOrNull { itemsToMove.contains(it) }
        lastItemId?.let {
            sendEffect(BaseHomeComponent.Effects.Common.ScrollToDownloadItem(it, true))
        }
    }

    fun reorderQueueItems(fromIndex: Int, toIndex: Int) {
        val downloadQueue = getCurrentDownloadQueue() ?: return
        val currentDraggingItem = runCatching {
            downloadQueue.getQueueItemFromOrder(fromIndex)
        }.getOrNull()
        val listOfIds = selectionList.value
            .let {
                if (currentDraggingItem != null && !it.contains(currentDraggingItem)) {
                    it.plus(currentDraggingItem)
                } else {
                    it
                }
            }

        val delta = toIndex - fromIndex
        downloadQueue.move(
            listOfIds, delta
        )
        val queueItems = downloadQueue.queueModel.value.queueItems
        val itemToScroll = if (delta > 0) {
            queueItems.lastOrNull { listOfIds.contains(it) }
        } else {
            queueItems.firstOrNull { listOfIds.contains(it) }
        }
        itemToScroll?.let {
            sendEffect(BaseHomeComponent.Effects.Common.ScrollToDownloadItem(it))
        }
    }

    fun removeQueueItems() {
        val downloadQueue = getCurrentDownloadQueue() ?: return
        val itemsToRemove = selectionList.value
        downloadQueue.removeFromQueue(itemsToRemove)
    }

    fun revealItem(downloadId: Long) {
        scope.launch {
            sendEffect(BaseHomeComponent.Effects.Common.ScrollToDownloadItem(downloadId))
        }
    }

    override val enterNewURLDialogManager: EnterNewURLDialogManager
        get() = this

    sealed interface FilterMode {
        data class Status(
            val downloadStatus: DownloadStatusCategoryFilter,
            val category: Category?,
        ) : FilterMode

        data class Queue(
            val queue: QueueModel,
        ) : FilterMode
    }

    sealed interface Effects : BaseHomeComponent.Effects.PlatformEffects {
        data class ShareFiles(
            val files: List<File>,
        ) : Effects
    }
}

sealed interface HomePopups {
    data object AddMenu : HomePopups
    data object MainMenu : HomePopups
    data object SortMenu : HomePopups
    data object FilterMenu : HomePopups
}
