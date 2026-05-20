package com.flowspeed.link.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import com.flowspeed.link.desktop.AppArguments
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.AppEffects
import com.flowspeed.link.desktop.actions.gotoSettingsAction
import com.flowspeed.link.desktop.actions.requestExitAction
import com.flowspeed.link.desktop.actions.showDownloadList
import com.flowspeed.link.desktop.pages.about.ShowAboutDialog
import com.flowspeed.link.desktop.pages.adddownload.ShowAddDownloadDialogs
import com.flowspeed.link.desktop.pages.batchdownload.BatchDownloadWindow
import com.flowspeed.link.desktop.pages.category.ShowCategoryDialogs
import com.flowspeed.link.desktop.pages.home.ConfirmExit
import com.flowspeed.link.desktop.pages.editdownload.EditDownloadWindow
import com.flowspeed.link.desktop.pages.enterurl.EnterNewDownloadWindow
import com.flowspeed.link.desktop.pages.about.ShowOpenSourceLibraries
import com.flowspeed.link.desktop.pages.checksum.FileChecksumWindow
import com.flowspeed.link.desktop.pages.home.HomeWindow
import com.flowspeed.link.desktop.pages.newqueue.NewQueueDialog
import com.flowspeed.link.desktop.pages.perhostsettings.PerHostSettingsWindow
import com.flowspeed.link.desktop.pages.queue.QueuesWindow
import com.flowspeed.link.desktop.pages.settings.FontManager
import com.flowspeed.link.desktop.pages.settings.SettingWindow
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.link.desktop.pages.poweractionalert.PowerActionAlert
import com.flowspeed.link.desktop.pages.singledownloadpage.ShowDownloadDialogs
import com.flowspeed.link.desktop.pages.updater.ShowUpdaterDialog
import com.flowspeed.link.desktop.ui.configurable.common.CommonConfigurableRenderersForDesktop
import com.flowspeed.link.desktop.ui.configurable.platform.PlatformConfigurableRenderersForDesktop
import com.flowspeed.link.desktop.ui.widget.Tray
import com.flowspeed.link.desktop.ui.widget.ShowMessageDialogs
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.desktop.utils.GlobalAppExceptionHandler
import com.flowspeed.link.desktop.utils.MemoryManager
import com.flowspeed.link.desktop.utils.ProvideGlobalExceptionHandler
import com.flowspeed.link.desktop.utils.isInDebugMode
import com.flowspeed.link.shared.ui.ProvideCommonSettings
import com.flowspeed.link.shared.ui.ProvideSizeUnits
import com.flowspeed.link.shared.ui.configurable.ConfigurableRendererRegistry
import com.flowspeed.link.shared.ui.theme.FlowTheme
import com.flowspeed.link.shared.ui.widget.NotificationManager
import com.flowspeed.link.shared.ui.widget.ProvideLanguageManager
import com.flowspeed.link.shared.ui.widget.ProvideNotificationManager
import com.flowspeed.link.shared.ui.widget.useNotification
import com.flowspeed.link.shared.util.mvi.HandleEffects
import com.flowspeed.link.shared.util.ui.ProvideDebugInfo
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.util.compose.action.buildMenu
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import com.flowspeed.lib.util.desktop.PlatformDockToggler
import com.flowspeed.lib.util.desktop.mac.event.MacEventHandler
import com.flowspeed.lib.util.platform.Platform
import com.flowspeed.lib.util.platform.isMac
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

/**
 * UI lifecycle manager.
 *
 * Implements a "dispose on idle" strategy:
 * - When no windows are visible → Compose runtime is NOT running, only AWT tray
 * - When user opens a window → Compose `application {}` starts, full UI available
 * - When all windows close → Compose exits, back to lightweight AWT tray
 *
 * This reduces idle RAM from ~260MB to ~60-80MB by releasing Skia/Compose memory.
 */
object Ui : KoinComponent {
    val scope: CoroutineScope by inject()
    private val memoryManager: MemoryManager by inject()

    /** Signals that the Compose UI should start (set to true when window needed). */
    private val composeUiRequested = MutableStateFlow(false)

    private var lightweightTray: LightweightTray? = null

    fun boot(
        appArguments: AppArguments,
        globalAppExceptionHandler: GlobalAppExceptionHandler,
    ) {
        val appComponent: AppComponent = get()
        val themeManager: ThemeManager = get()
        val fontManager: FontManager = get()
        val languageManager: LanguageManager = get()
        val notificationManager: NotificationManager = get()
        themeManager.boot()
        fontManager.boot()
        languageManager.boot()

        if (Platform.isMac()) {
            MacEventHandler.configure(
                onClickIcon = { requestComposeUi(appComponent) },
                onAboutClick = { appComponent.showAboutPage.value = true },
                onSettingsClick = appComponent::openSettings,
                onQuit = { scope.launch { appComponent.requestExitApp() } }
            )
        }

        // Track UI visibility for memory management
        scope.launch {
            appComponent.showHomeSlot.collect { slot ->
                memoryManager.setUiVisible(slot.child != null)
            }
        }

        // Wake up UI when an Add Download dialog is requested via browser integration
        scope.launch {
            appComponent.openedAddDownloadDialogs.collect { dialogs ->
                if (dialogs.isNotEmpty() && !composeUiRequested.value) {
                    composeUiRequested.value = true
                }
            }
        }

        if (appArguments.startSilent) {
            // Background mode: show lightweight AWT tray, no Compose loaded
            showLightweightTray(appComponent)
            // Block main thread waiting for Compose UI to be requested
            runComposeLoop(appComponent, themeManager, fontManager, languageManager, notificationManager, globalAppExceptionHandler)
        } else {
            // Normal mode: open window immediately
            appComponent.openHome()
            composeUiRequested.value = true
            runComposeLoop(appComponent, themeManager, fontManager, languageManager, notificationManager, globalAppExceptionHandler)
        }
    }

    /**
     * Main loop: alternates between lightweight tray (idle) and Compose UI (active).
     * When Compose `application {}` exits (all windows closed + tray dismissed),
     * we go back to lightweight tray and wait for next activation.
     */
    private fun runComposeLoop(
        appComponent: AppComponent,
        themeManager: ThemeManager,
        fontManager: FontManager,
        languageManager: LanguageManager,
        notificationManager: NotificationManager,
        globalAppExceptionHandler: GlobalAppExceptionHandler,
    ) {
        while (true) {
            // Wait until Compose UI is requested
            if (!composeUiRequested.value) {
                kotlinx.coroutines.runBlocking {
                    composeUiRequested.first { it }
                }
            }

            // Hide lightweight tray before starting Compose (Compose has its own tray)
            lightweightTray?.hide()
            lightweightTray = null

            // Run Compose application - blocks until exitApplication() is called
            application(exitProcessOnExit = false) {
                ProvideLocalProviders(
                    languageManager = languageManager,
                    appComponent = appComponent,
                    themeManager = themeManager,
                    fontManager = fontManager,
                    globalAppExceptionHandler = globalAppExceptionHandler,
                    notificationManager = notificationManager,
                ) {
                    HandleEffectsForApp(appComponent)
                    SystemTray(appComponent, onAllWindowsClosed = {
                        // When user closes all windows and system tray is active,
                        // exit Compose to free Skia memory
                        scope.launch {
                            composeUiRequested.value = false
                            exitApplication()
                        }
                    })
                    RenderAllWindows(appComponent)
                }
            }

            // Compose exited - trigger GC to free Skia/Compose memory
            memoryManager.setUiVisible(false)
            System.gc()
            System.runFinalization()
            System.gc()

            // Show lightweight tray again
            showLightweightTray(appComponent)
        }
    }

    private fun requestComposeUi(appComponent: AppComponent) {
        appComponent.openHome()
        composeUiRequested.value = true
    }

    private fun showLightweightTray(appComponent: AppComponent) {
        if (lightweightTray != null) return
        lightweightTray = LightweightTray(
            tooltip = AppInfo.displayName,
            onShowWindow = { requestComposeUi(appComponent) },
            onOpenSettings = {
                appComponent.openSettings()
                requestComposeUi(appComponent)
            },
            onExit = { scope.launch { appComponent.requestExitApp() } },
        ).also { it.show() }
    }

    @Composable
    private fun ApplicationScope.RenderAllWindows(appComponent: AppComponent) {
        val showHomeSlot = appComponent.showHomeSlot.collectAsState().value
        showHomeSlot.child?.instance?.let {
            HomeWindow(it, appComponent::closeHome)
        }
        val showSettingSlot = appComponent.showSettingSlot.collectAsState().value
        showSettingSlot.child?.instance?.let {
            SettingWindow(it, appComponent::closeSettings)
        }
        val showQueuesSlot = appComponent.showQueuesSlot.collectAsState().value
        showQueuesSlot.child?.instance?.let {
            QueuesWindow(it)
        }
        val batchDownloadSlot = appComponent.batchDownloadSlot.collectAsState().value
        batchDownloadSlot.child?.instance?.let {
            BatchDownloadWindow(it)
        }
        val editDownloadSlot = appComponent.editDownloadSlot.collectAsState().value
        editDownloadSlot.child?.instance?.let {
            EditDownloadWindow(it)
        }
        EnterNewDownloadWindow(appComponent)
        ShowAddDownloadDialogs(appComponent)
        ShowDownloadDialogs(appComponent)
        ShowCategoryDialogs(appComponent)
        FileChecksumWindow(appComponent)
        ShowUpdaterDialog(appComponent.updater)
        ShowAboutDialog(appComponent)
        NewQueueDialog(appComponent)
        ShowMessageDialogs(appComponent)
        ShowOpenSourceLibraries(appComponent)
        ConfirmExit(appComponent)
        PowerActionAlert(appComponent)
        PerHostSettingsWindow(appComponent)
    }
}

@Composable
private fun ProvideLocalProviders(
    languageManager: LanguageManager,
    themeManager: ThemeManager,
    fontManager: FontManager,
    appComponent: AppComponent,
    notificationManager: NotificationManager,
    globalAppExceptionHandler: GlobalAppExceptionHandler,
    content: @Composable () -> Unit
) {
    val theme by themeManager.currentThemeColor.collectAsState()
    val fontFamily by fontManager.currentFontFamily.collectAsState()
    val configurableRendererRegistry = remember {
        ConfigurableRendererRegistry {
            listOf(
                PlatformConfigurableRenderersForDesktop,
                CommonConfigurableRenderersForDesktop,
            ).forEach {
                it.getAllRenderers().forEach { (key, renderer) ->
                    this.register(key, renderer)
                }
            }
        }
    }
    ProvideDebugInfo(AppInfo.isInDebugMode()) {
        ProvideLanguageManager(languageManager) {
            ProvideCommonSettings(
                appSettings = appComponent.appSettings,
                configurableRendererRegistry = configurableRendererRegistry,
                iconProvider = appComponent.iconFromUriResolver
            ) {
                ProvideNotificationManager(notificationManager) {
                    FlowTheme(
                        myColors = theme,
                        fontFamily = fontFamily,
                        uiScale = appComponent.uiScale.collectAsState().value
                    ) {
                        ProvideGlobalExceptionHandler(globalAppExceptionHandler) {
                            ProvideSizeUnits(appComponent.appRepository) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandleEffectsForApp(appComponent: AppComponent) {
    val notificationManager = useNotification()
    val scope = rememberCoroutineScope()
    HandleEffects(appComponent) {
        when (it) {
            is AppEffects.SimpleNotificationNotification -> {
                scope.launch {
                    withTimeout(5000) {
                        notificationManager.showNotification(it.notificationModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationScope.SystemTray(
    component: AppComponent,
    onAllWindowsClosed: () -> Unit,
) {
    val useSystemTray by component.useSystemTray.collectAsState()
    
    val showHomeSlot by component.showHomeSlot.collectAsState()
    val showSettingSlot by component.showSettingSlot.collectAsState()
    val showQueuesSlot by component.showQueuesSlot.collectAsState()
    val batchDownloadSlot by component.batchDownloadSlot.collectAsState()
    val editDownloadSlot by component.editDownloadSlot.collectAsState()
    val openedAddDownloadDialogs by component.openedAddDownloadDialogs.collectAsState()
    val openedDownloadDialogs by component.openedDownloadDialogs.collectAsState()

    val hasAnyWindow = showHomeSlot.child != null ||
            showSettingSlot.child != null ||
            showQueuesSlot.child != null ||
            batchDownloadSlot.child != null ||
            editDownloadSlot.child != null ||
            openedAddDownloadDialogs.isNotEmpty() ||
            openedDownloadDialogs.isNotEmpty()

    if (useSystemTray) {
        LaunchedEffect(Unit) { PlatformDockToggler.hide() }
        val menu = remember {
            buildMenu {
                +showDownloadList
                +gotoSettingsAction
                +requestExitAction
            }
        }
        Tray(
            icon = MyIcons.appIcon,
            tooltip = AppInfo.displayName,
            primaryAction = { showDownloadList.onClick() },
            menu = menu,
        )

        // When all windows are closed and system tray is enabled,
        // signal to dispose Compose and switch to lightweight tray
        LaunchedEffect(hasAnyWindow) {
            if (!hasAnyWindow) {
                delay(500) // small delay to allow new windows (like progress dialog) to open or cancel if one does
                onAllWindowsClosed()
            }
        }
    } else {
        LaunchedEffect(Unit) { PlatformDockToggler.show() }
    }
}
