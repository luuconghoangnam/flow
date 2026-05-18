package com.flowspeed.link.android.ui

import android.content.Context
import android.content.Intent
import com.flowspeed.link.UpdateManager
import com.flowspeed.link.android.pages.add.multiple.AddMultiDownloadActivity
import com.flowspeed.link.android.pages.add.single.AddSingleDownloadActivity
import com.flowspeed.link.android.pages.batchdownload.AndroidBatchDownloadComponent
import com.flowspeed.link.android.pages.browser.BrowserActivity
import com.flowspeed.link.android.pages.checksum.AndroidFileChecksumComponent
import com.flowspeed.link.android.pages.editdownload.AndroidEditDownloadComponent
import com.flowspeed.link.android.pages.home.HomeComponent
import com.flowspeed.link.android.pages.onboarding.initialsetup.InitialSetupComponent
import com.flowspeed.link.android.pages.onboarding.permissions.PermissionComponent
import com.flowspeed.link.android.pages.onboarding.permissions.PermissionManager
import com.flowspeed.link.android.pages.perhostsettings.AndroidPerHostSettingsComponent
import com.flowspeed.link.android.pages.queue.QueueConfigurationComponent
import com.flowspeed.link.android.pages.settings.AndroidSettingsComponent
import com.flowspeed.link.android.pages.singledownload.SingleDownloadPageActivity
import com.flowspeed.link.android.storage.AndroidOnBoardingStorage
import com.flowspeed.link.android.storage.HomePageStorage
import com.flowspeed.link.android.ui.Screen.*
import com.flowspeed.link.android.util.FlowAppManager
import com.flowspeed.link.android.util.pagemanager.IBrowserPageManager
import com.flowspeed.link.android.util.pagemanager.PermissionsPageManager
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.pagemanager.AboutPageManager
import com.flowspeed.link.shared.pagemanager.AddDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.BatchDownloadPageManager
import com.flowspeed.link.shared.pagemanager.CategoryDialogManager
import com.flowspeed.link.shared.pagemanager.DownloadDialogManager
import com.flowspeed.link.shared.pagemanager.EditDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.FileChecksumDialogManager
import com.flowspeed.link.shared.pagemanager.NotificationSender
import com.flowspeed.link.shared.pagemanager.OpenSourceLibrariesPageManager
import com.flowspeed.link.shared.pagemanager.PerHostSettingsPageManager
import com.flowspeed.link.shared.pagemanager.QueuePageManager
import com.flowspeed.link.shared.pagemanager.SettingsPageManager
import com.flowspeed.link.shared.pages.adddownload.AddDownloadConfig
import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.link.shared.pages.adddownload.ImportOptions
import com.flowspeed.link.shared.pages.category.CategoryComponent
import com.flowspeed.link.shared.pages.updater.UpdateComponent
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.link.shared.ui.widget.MessageDialogType
import com.flowspeed.link.shared.ui.widget.NotificationModel
import com.flowspeed.link.shared.ui.widget.NotificationType
import com.flowspeed.link.shared.util.BaseComponent
import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.FileIconProvider
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.category.DefaultCategories
import com.flowspeed.link.shared.util.mvi.ContainsEffects
import com.flowspeed.link.shared.util.mvi.supportEffects
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.flowspeed.link.shared.util.subscribeAsStateFlow
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pushToFront
import com.flowspeed.lib.downloader.monitor.isDownloadActiveFlow
import com.flowspeed.lib.downloader.queue.DefaultQueueInfo
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import com.flowspeed.lib.util.flow.mapStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

sealed interface Screen {
    data class Home(
        val component: HomeComponent,
    ) : Screen

    data class Settings(
        val component: AndroidSettingsComponent,
    ) : Screen

    data object About : Screen

    data object OpenSourceThirdPartyLibraries : Screen

    data object Translators : Screen

    data class PerHostSettings(
        val component: AndroidPerHostSettingsComponent,
    ) : Screen

    data class FileChecksum(
        val component: AndroidFileChecksumComponent,
    ) : Screen

    data class InitialSetup(
        val component: InitialSetupComponent,
    ) : Screen

    data class Permissions(
        val component: PermissionComponent,
    ) : Screen
}

@Serializable
sealed interface ScreenConfig {
    @Serializable
    data object Home : ScreenConfig

    @Serializable
    data object Settings : ScreenConfig

    @Serializable
    data object About : ScreenConfig

    @Serializable
    data object OpenSourceThirdPartyLibraries : ScreenConfig

    @Serializable
    data object Translators : ScreenConfig

    @Serializable
    data class PerHostSettings(
        val config: AndroidPerHostSettingsComponent.Config
    ) : ScreenConfig

    @Serializable
    data class FileChecksum(
        val config: AndroidFileChecksumComponent.Config
    ) : ScreenConfig

    @Serializable
    data object InitialSetup : ScreenConfig

    @Serializable
    data class Permissions(
        val openHomeAfterFinish: Boolean,
    ) : ScreenConfig
}

class MainComponent(
    ctx: ComponentContext,
    private val context: Context,
    private val downloadItemOpener: DownloadItemOpener,
    private val downloadSystem: DownloadSystem,
    private val categoryManager: CategoryManager,
    private val queueManager: QueueManager,
    private val defaultCategories: DefaultCategories,
    private val fileIconProvider: FileIconProvider,
    private val downloaderInUiRegistry: DownloaderInUiRegistry,
    private val perHostSettingsManager: PerHostSettingsManager,
    private val applicationScope: CoroutineScope,
    private val appRepository: BaseAppRepository,
    private val updateManager: UpdateManager,
    private val permissionManager: PermissionManager,
    private val languageManager: LanguageManager,
    private val themeManager: ThemeManager,
    val FlowAppManager: FlowAppManager,
    val onBoardingStorage: AndroidOnBoardingStorage,
    val homePageStorage: HomePageStorage,
    private val json: Json,
) : BaseComponent(ctx),
    DownloadDialogManager,
    EditDownloadDialogManager,
    AddDownloadDialogManager,
    FileChecksumDialogManager,
    QueuePageManager,
    CategoryDialogManager,
    NotificationSender,
    SettingsPageManager,
    OpenSourceLibrariesPageManager,
    AboutPageManager,
    BatchDownloadPageManager,
    PerHostSettingsPageManager,
    PermissionsPageManager,
    IBrowserPageManager,
    ContainsEffects<MainComponent.MainAppEffects> by supportEffects() {
    val categoryComponentNavigation = SlotNavigation<Long>()
    val categorySlot = childSlot(
        source = categoryComponentNavigation,
        key = "categoryEdit",
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
    val queueConfigComponentNavigation = SlotNavigation<Long>()
    val queueConfigSlot = childSlot(
        source = queueConfigComponentNavigation,
        key = "queueConfigs",
        childFactory = { config, ctx ->
            QueueConfigurationComponent(
                ctx = ctx,
                id = config,
                queueManager = queueManager,
            )
        },
        serializer = Long.serializer(),
    ).subscribeAsStateFlow()

    val batchDownloadNavigation = SlotNavigation<Unit>()
    val batchDownloadSlot = childSlot(
        source = batchDownloadNavigation,
        key = "batchDownload",
        childFactory = { config, ctx ->
            AndroidBatchDownloadComponent(
                ctx = ctx,
                onClose = ::closeBatchDownload,
                importLinks = { links ->
                    openAddDownloadDialog(
                        links.mapNotNull { link ->
                            downloaderInUiRegistry
                                .bestMatchForThisLink(link)
                                ?.createMinimumCredentials(link)
                                ?.let { credentials ->
                                    AddDownloadCredentialsInUiProps(
                                        credentials = credentials,
                                    )
                                }
                        }
                    )
                }
            )
        },
        serializer = null,
    ).subscribeAsStateFlow()
    val editDownloadNavigation = SlotNavigation<Long>()
    val editDownloadSlot = childSlot(
        source = editDownloadNavigation,
        key = "editDownload",
        childFactory = { editDownloadConfig: Long, componentContext: ComponentContext ->
            AndroidEditDownloadComponent(
                ctx = componentContext,
                onRequestClose = {
                    closeEditDownloadDialog()
                },
                onEdited = { updater, downloadJobExtraConfig ->
                    scope.launch {
                        downloadSystem.editDownload(
                            id = editDownloadConfig,
                            applyUpdate = updater,
                            downloadJobExtraConfig = downloadJobExtraConfig
                        )
                        closeEditDownloadDialog()
                    }
                },
                downloadId = editDownloadConfig,
                acceptEdit = downloadSystem.downloadMonitor
                    .isDownloadActiveFlow(editDownloadConfig)
                    .mapStateFlow { !it },
                downloadSystem = downloadSystem,
                downloaderInUiRegistry = downloaderInUiRegistry,
                iconProvider = fileIconProvider,
            )
        },
        serializer = null,
    ).subscribeAsStateFlow()

    val updaterComponent = UpdateComponent(
        childContext("updater"),
        this,
        updateManager,
    )

    val stackNavigation = StackNavigation<ScreenConfig>()
    val stack = childStack(
        stackNavigation,
        key = "mainStack",
        serializer = ScreenConfig.serializer(),
        initialStack = {
            val initialConfigPassed = onBoardingStorage.initialSetupPassed.value
            val firstPage = if (!initialConfigPassed) {
                ScreenConfig.InitialSetup
            } else {
                if (shouldGoToPermissionsPage()) {
                    ScreenConfig.Permissions(openHomeAfterFinish = true)
                } else {
                    ScreenConfig.Home
                }
            }
            listOf(firstPage)
        },
        handleBackButton = true,
        childFactory = { cfg, ctx ->
            when (cfg) {
                ScreenConfig.Home -> {
                    Home(
                        HomeComponent(
                            componentContext = ctx,
                            downloadItemOpener = downloadItemOpener,
                            downloadDialogManager = this,
                            editDownloadDialogManager = this,
                            addDownloadDialogManager = this,
                            fileChecksumDialogManager = this,
                            queuePageManager = this,
                            categoryDialogManager = this,
                            notificationSender = this,
                            downloadSystem = downloadSystem,
                            categoryManager = categoryManager,
                            queueManager = queueManager,
                            defaultCategories = defaultCategories,
                            fileIconProvider = fileIconProvider,
                            openSourceLibrariesPageManager = this,
                            aboutPageManager = this,
                            batchDownloadPageManager = this,
                            settingsPageManager = this,
                            perHostSettingsPageManager = this,
                            downloaderInUiRegistry = downloaderInUiRegistry,
                            updateComponent = updaterComponent,
                            homePageStorage = homePageStorage,
                            browserPageManager = this,
                        )
                    )
                }

                ScreenConfig.Settings -> {
                    Settings(
                        AndroidSettingsComponent(
                            ctx = ctx,
                            perHostSettingsPageManager = this,
                            permissionsPageManager = this,
                        )
                    )
                }

                ScreenConfig.About -> {
                    About
                }

                ScreenConfig.OpenSourceThirdPartyLibraries -> {
                    OpenSourceThirdPartyLibraries
                }

                ScreenConfig.Translators -> {
                    Translators
                }

                is ScreenConfig.PerHostSettings -> {
                    PerHostSettings(
                        AndroidPerHostSettingsComponent(
                            ctx = ctx,
                            perHostSettingsManager = perHostSettingsManager,
                            appRepository = appRepository,
                            appScope = applicationScope,
                            closeRequested = ::closePerHostSettings
                        )
                    )
                }

                is ScreenConfig.FileChecksum -> {
                    FileChecksum(
                        AndroidFileChecksumComponent(
                            ctx = ctx,
                            id = cfg.config.id,
                            itemIds = cfg.config.itemIds,
                            closeComponent = {
                                closeFileChecksumPage(cfg.config.id)
                            },
                            downloadSystem = downloadSystem,
                            iconProvider = fileIconProvider,
                        )
                    )
                }

                ScreenConfig.InitialSetup -> {
                    Screen.InitialSetup(
                        InitialSetupComponent(
                            ctx = ctx,
                            languageManager = languageManager,
                            themeManager = themeManager,
                            onFinish = {
                                afterInitialFinish()
                            }
                        )
                    )
                }

                is ScreenConfig.Permissions -> {
                    Screen.Permissions(
                        PermissionComponent(
                            componentContext = ctx,
                            permissionManager = permissionManager,
                            onReady = {
                                onPermissionsReady(cfg.openHomeAfterFinish)
                            },
                            onDismiss = {
                                closePermissionsPage()
                            }
                        )
                    )
                }
            }
        },
    ).subscribeAsStateFlow()

    private fun onPermissionsReady(openHomeAfterFinish: Boolean) {
        if (openHomeAfterFinish) {
            onBoardingStorage.permissionsPassedAtLeastOnce.value = true
            scope.launch {
                FlowAppManager.startDownloadSystem()
                FlowAppManager.startOurService()
                initiallyGoToHome()
            }
        } else {
            closePermissionsPage()
        }
    }

    private fun shouldGoToPermissionsPage(): Boolean {
        val permissionsPassedAtLeastOnce = onBoardingStorage.permissionsPassedAtLeastOnce.value
        if (!permissionsPassedAtLeastOnce) {
            return true
        }
        return !permissionManager.isReady()
    }

    private fun afterInitialFinish() {
        onBoardingStorage.initialSetupPassed.value = true
        if (shouldGoToPermissionsPage()) {
            openPermissionsPage(true)
        } else {
            initiallyGoToHome()
        }
    }


    private fun initiallyGoToHome() {
        scope.launch {
            stackNavigation.navigate {
                listOf(ScreenConfig.Home)
            }
        }
    }

    override fun openDownloadDialog(id: Long) {
        sendEffect(
            MainAppEffects.StartActivity(
                SingleDownloadPageActivity.createIntent(
                    context = context,
                    downloadId = id,
                    comingFromOutside = false
                )
            )
        )
    }

    override fun closeDownloadDialog() {
        TODO("Not yet implemented")
    }

    override fun openEditDownloadDialog(id: Long) {
        scope.launch {
            editDownloadNavigation.activate(id)
        }
    }

    override fun closeEditDownloadDialog() {
        scope.launch {
            editDownloadNavigation.dismiss()
        }
    }

    override fun closeAddDownloadDialog() {
        TODO("Not yet implemented")
    }

    override fun openAddDownloadDialog(
        links: List<AddDownloadCredentialsInUiProps>,
        importOptions: ImportOptions
    ) {
        scope.launch {
            when (links.size) {
                0 -> return@launch
                1 -> {
                    val intent = AddSingleDownloadActivity.createIntent(
                        context = context,
                        singleAddConfig = AddDownloadConfig.SingleAddConfig(
                            newDownload = links.first(),
                            importOptions = importOptions,
                        ),
                        json = json,
                    )
                    sendEffect(MainAppEffects.StartActivity(intent))
                }

                else -> {
                    val intent = AddMultiDownloadActivity.createIntent(
                        context = context,
                        multipleAddConfig = AddDownloadConfig.MultipleAddConfig(
                            newDownloads = links,
                            importOptions = importOptions,
                        ),
                        json = json,
                    )
                    sendEffect(MainAppEffects.StartActivity(intent))
                }
            }
        }
    }

    override fun openFileChecksumPage(ids: List<Long>) {
        scope.launch {
            stackNavigation.pushToFront(
                ScreenConfig.FileChecksum(
                    AndroidFileChecksumComponent.Config(
                        itemIds = ids,
                    )
                )
            )
        }
    }

    override fun closeFileChecksumPage(dialogId: String) {
        scope.launch {
            stackNavigation.navigate {
                it.filterNot { config ->
                    config is ScreenConfig.FileChecksum
                }
            }
        }
    }

    override fun openQueues(openQueueId: Long?) {
        scope.launch {
            queueConfigComponentNavigation.activate(openQueueId ?: DefaultQueueInfo.ID)
        }
    }

    override fun closeQueues() {
        scope.launch {
            queueConfigComponentNavigation.dismiss()
        }
    }

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

    override fun sendDialogNotification(
        title: StringSource,
        description: StringSource,
        type: MessageDialogType
    ) {
        sendNotification(
            tag = title,
            title = title,
            description = description,
            type = when (type) {
                MessageDialogType.Error -> NotificationType.Error
                MessageDialogType.Info -> NotificationType.Info
                MessageDialogType.Success -> NotificationType.Success
                MessageDialogType.Warning -> NotificationType.Warning
            },
        )
    }

    override fun openSettings() {
        scope.launch {
            stackNavigation.pushToFront(ScreenConfig.Settings)
        }
    }

    override fun closeSettings() {
        scope.launch {
            stackNavigation.navigate {
                it.filterNot { config ->
                    config is ScreenConfig.Settings
                }
            }
        }
    }

    override fun sendNotification(
        tag: Any,
        title: StringSource,
        description: StringSource,
        type: NotificationType
    ) {
        sendEffect(
            MainAppEffects.SimpleNotificationNotification(
                NotificationModel(
                    tag = tag,
                    initialTitle = title,
                    initialDescription = description,
                    initialNotificationType = type,
                )
            )
        )
    }

    override fun openOpenSourceLibrariesPage() {
        scope.launch {
            stackNavigation.pushToFront(ScreenConfig.OpenSourceThirdPartyLibraries)
        }
    }

    override fun openAboutPage() {
        scope.launch {
            stackNavigation.pushToFront(ScreenConfig.About)
        }
    }

    override fun openBatchDownloadPage() {
        scope.launch {
            batchDownloadNavigation.activate(Unit)
        }
    }

    override fun closeBatchDownload() {
        scope.launch {
            batchDownloadNavigation.dismiss()
        }
    }

    override fun openPerHostSettings(openedHost: String?) {
        scope.launch {
            stackNavigation.pushToFront(
                ScreenConfig.PerHostSettings(
                    AndroidPerHostSettingsComponent.Config(openedHost)
                )
            )
        }
    }

    override fun closePerHostSettings() {
        scope.launch {
            stackNavigation.navigate {
                it.filterNot { config ->
                    config is ScreenConfig.PerHostSettings
                }
            }
        }
    }

    override fun openPermissionsPage(
        openHomeAfterFinish: Boolean
    ) {
        scope.launch {
            stackNavigation.pushToFront(
                ScreenConfig.Permissions(openHomeAfterFinish)
            )
        }
    }

    override fun closePermissionsPage() {
        scope.launch {
            stackNavigation.navigate {
                val newList = it.filterNot { config ->
                    config is ScreenConfig.Permissions
                }
                newList.ifEmpty {
                    listOf(ScreenConfig.InitialSetup)
                }
            }
        }
    }


    private val _showAddQueue = MutableStateFlow(false)
    val showAddQueue = _showAddQueue.asStateFlow()
    fun setShowAddQueue(value: Boolean) {
        _showAddQueue.value = value
    }

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

    fun revealDownload(downloadId: Long) {
        stackNavigation.pushToFront(
            ScreenConfig.Home,
        ) {
            if (downloadId < 0) {
                return@pushToFront
            }
            stack.value.items
                .lastOrNull()
                ?.let {
                    (it.instance as? Screen.Home)?.component?.revealItem(downloadId)
                }
        }
    }

    override fun openBrowser(url: String?) {
        val intent = BrowserActivity.createIntent(
            context = context,
            url = url,
        )
        sendEffect(MainAppEffects.StartActivity(intent))
    }

    sealed interface MainAppEffects {
        data class StartActivity(val intent: Intent) : MainAppEffects
        data class SimpleNotificationNotification(val notificationModel: NotificationModel) : MainAppEffects
    }
}
