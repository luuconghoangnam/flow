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
        if (appArguments.startSilent) {
            // Background mode: NO Compose, NO AppComponent, NO Skia loaded
            // Only show lightweight AWT tray and block until UI is requested
            bootBackgroundMode(globalAppExceptionHandler)
        } else {
            // Normal mode: load full UI immediately
            bootFullUiMode(globalAppExceptionHandler)
        }
    }

    /**
     * Background mode: minimal memory footprint.
     * Only AWT tray is shown. Compose/Skia not loaded until user clicks tray.
     */
    private fun bootBackgroundMode(globalAppExceptionHandler: GlobalAppExceptionHandler) {
        val latch = java.util.concurrent.CountDownLatch(1)

        val tray = LightweightTray(
            tooltip = "Flow Download Manager",
            onShowWindow = {
                composeUiRequested.value = true
                latch.countDown()
            },
            onOpenSettings = {
                composeUiRequested.value = true
                latch.countDown()
            },
            onExit = {
                System.exit(0)
            },
        )
        tray.show()
        lightweightTray = tray

        // Block main thread until user clicks tray (AWT events still process on EDT)
        latch.await()

        // User requested UI → hide tray and boot full UI
        tray.hide()
        lightweightTray = null
        bootFullUiMode(globalAppExceptionHandler)
    }

    /**
     * Full UI mode: loads Compose, AppComponent, Skia - the full experience.
     * When user closes the window, the process exits (Go service handles tray).
     */
    private fun bootFullUiMode(globalAppExceptionHandler: GlobalAppExceptionHandler) {
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
                onClickIcon = { appComponent.activateHomeIfNotOpen() },
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

        appComponent.openHome()

        // If launched with --add-download URL, open the add-download dialog
        val addUrl = AppArguments.get().addDownloadUrl
        if (addUrl != null) {
            scope.launch {
                delay(500)
                val downloaderInUiRegistry: com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry = get()
                val credentials = downloaderInUiRegistry
                    .bestMatchForThisLink(addUrl)
                    ?.createMinimumCredentials(addUrl)
                if (credentials != null) {
                    appComponent.openAddDownloadDialog(
                        links = listOf(
                            com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps(
                                credentials = credentials
                            )
                        )
                    )
                }
            }
        }

        // Run Compose UI - when window closes, exit process
        // Go service handles tray icon and will relaunch us when needed
        application(exitProcessOnExit = true) {
            ProvideLocalProviders(
                languageManager = languageManager,
                appComponent = appComponent,
                themeManager = themeManager,
                fontManager = fontManager,
                globalAppExceptionHandler = globalAppExceptionHandler,
                notificationManager = notificationManager,
            ) {
                HandleEffectsForApp(appComponent)
                // NO system tray in UI process - Go service owns the tray
                RenderAllWindows(appComponent)

                // When home window is closed, exit the UI process
                val hasHomeWindow = appComponent.showHomeSlot.collectAsState().value.child != null
                LaunchedEffect(hasHomeWindow) {
                    if (!hasHomeWindow) {
                        // Small delay to avoid exit during initial composition
                        delay(500)
                        exitApplication()
                    }
                }
            }
        }
        // If we get here, Compose exited → process will exit
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
                            delay(500) // small delay to avoid flicker
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

            // Start Go service to handle extension requests while UI is closed
            com.flowspeed.link.desktop.bootstrap.ServiceProcessManager.ensureRunning()

            // Show lightweight tray again
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
    }

    private fun requestComposeUi(appComponent: AppComponent) {
        // Stop Go service - UI will take over the integration port
        com.flowspeed.link.desktop.bootstrap.ServiceProcessManager.stop()
        appComponent.openHome()
        composeUiRequested.value = true
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
    val hasHomeWindow = component.showHomeSlot.collectAsState().value.child != null

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

        // When home window is closed and system tray is enabled,
        // signal to dispose Compose and switch to lightweight tray
        LaunchedEffect(hasHomeWindow) {
            if (!hasHomeWindow) {
                onAllWindowsClosed()
            }
        }
    } else {
        LaunchedEffect(Unit) { PlatformDockToggler.show() }
    }
}
