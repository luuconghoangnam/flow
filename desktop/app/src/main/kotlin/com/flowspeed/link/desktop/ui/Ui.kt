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
import com.flowspeed.link.desktop.pages.confirmexit.ConfirmExit
import com.flowspeed.link.desktop.pages.editdownload.EditDownloadWindow
import com.flowspeed.link.desktop.pages.enterurl.EnterNewDownloadWindow
import com.flowspeed.link.desktop.pages.externallibs.ShowOpenSourceLibraries
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

object Ui : KoinComponent {
    val scope: CoroutineScope by inject()
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
        if (!appArguments.startSilent) {
            appComponent.openHome()
        }
        if (Platform.isMac()) {
            MacEventHandler.configure(
                onClickIcon = appComponent::activateHomeIfNotOpen,
                onAboutClick = {
                    appComponent.showAboutPage.value = true
                },
                onSettingsClick = appComponent::openSettings,
                onQuit = {
                    scope.launch { appComponent.requestExitApp() }
                }
            )
        }
        application {
            ProvideLocalProviders(
                languageManager = languageManager,
                appComponent = appComponent,
                themeManager = themeManager,
                fontManager = fontManager,
                globalAppExceptionHandler = globalAppExceptionHandler,
                notificationManager = notificationManager,
            ) {
                HandleEffectsForApp(appComponent)
                SystemTray(appComponent)
                val showHomeSlot =
                    appComponent.showHomeSlot.collectAsState().value
                showHomeSlot.child?.instance?.let {
                    HomeWindow(it, appComponent::closeHome)
                }
                val showSettingSlot =
                    appComponent.showSettingSlot.collectAsState().value
                showSettingSlot.child?.instance?.let {
                    SettingWindow(it, appComponent::closeSettings)
                }
                val showQueuesSlot =
                    appComponent.showQueuesSlot.collectAsState().value
                showQueuesSlot.child?.instance?.let {
                    QueuesWindow(it)
                }
                val batchDownloadSlot =
                    appComponent.batchDownloadSlot.collectAsState().value
                batchDownloadSlot.child?.instance?.let {
                    BatchDownloadWindow(it)
                }
                val editDownloadSlot =
                    appComponent.editDownloadSlot.collectAsState().value
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
) {
    val useSystemTray by component.useSystemTray.collectAsState()
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
    } else {
        LaunchedEffect(Unit) { PlatformDockToggler.show() }
    }
}
