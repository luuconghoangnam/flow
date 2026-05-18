package com.flowspeed.link.desktop.actions

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.desktop.di.Di
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.desktop.utils.DesktopEntryCreator
import com.flowspeed.link.desktop.utils.isAppInstalled
import com.flowspeed.link.desktop.window.Browser
import com.flowspeed.lib.util.compose.action.MenuItem
import com.flowspeed.lib.util.compose.action.buildMenu
import com.flowspeed.lib.util.compose.action.simpleAction
import com.flowspeed.link.shared.util.getIcon
import com.flowspeed.link.shared.util.getName
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.action.createCheckForUpdateAction
import com.flowspeed.link.shared.action.createDownloadFromClipboardAction
import com.flowspeed.link.shared.action.createNewDownloadAction
import com.flowspeed.link.shared.action.createNewQueueAction
import com.flowspeed.link.shared.action.createOpenAboutPage
import com.flowspeed.link.shared.action.createOpenBatchDownloadAction
import com.flowspeed.link.shared.action.createOpenOpenSourceThirdPartyLibrariesPage
import com.flowspeed.link.shared.action.createOpenQueuesAction
import com.flowspeed.link.shared.action.createOpenSettingsAction
import com.flowspeed.link.shared.action.createPerHostSettingsPage
import com.flowspeed.link.shared.action.createRequestExitAction
import com.flowspeed.link.shared.action.createStartQueueGroupAction
import com.flowspeed.link.shared.action.createStopQueueGroupAction
import com.flowspeed.lib.downloader.queue.activeQueuesFlow
import com.flowspeed.lib.util.URLOpener
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.desktop.PlatformAppActivator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.koin.core.component.get

private val appComponent = Di.get<AppComponent>()
private val scope = Di.get<CoroutineScope>()
private val downloadSystem = appComponent.downloadSystem

private val activeQueuesFlow = downloadSystem
    .queueManager
    .activeQueuesFlow()
    .stateIn(
        scope,
        SharingStarted.WhileSubscribed(),
        emptyList()
    )

// desktop
val stopAllAction = createDesktopStopAllAction(scope, downloadSystem, appComponent, activeQueuesFlow)
val newDownloadAction = createNewDownloadAction(appComponent)
val newDownloadFromClipboardAction = createDownloadFromClipboardAction(appComponent)
val createDesktopEntryAction = simpleAction(
    Res.string.create_desktop_entry.asStringSource(),
    MyIcons.applicationFile,
    checkEnable = MutableStateFlow(AppInfo.isAppInstalled())
) {
    DesktopEntryCreator.createLinuxDesktopEntry()
}
val showDownloadList = simpleAction(
    Res.string.show_downloads.asStringSource(),
    MyIcons.download,
) {
    PlatformAppActivator.active()
    appComponent.openHome()
}
val browserIntegrations = MenuItem.SubMenu(
    title = Res.string.download_browser_integration.asStringSource(),
    icon = MyIcons.download,
    items = buildMenu {
        for (browserExtension in SharedConstants.browserIntegrations) {
            item(
                title = browserExtension.type.getName().asStringSource(),
                icon = browserExtension.type.getIcon(),
                onClick = {
                    val browser = Browser.getBrowserByType(browserExtension.type)
                    val success = browser?.openLink(browserExtension.url) == true
                    if (!success) {
                        URLOpener.openUrl(browserExtension.url)
                    }
                }
            )
        }
    }
)


// commonUsage but with desktop implementations
val newQueueAction = createNewQueueAction(scope, appComponent)
val openQueuesAction = createOpenQueuesAction(appComponent)
val openAboutAction = createOpenAboutPage(appComponent)
val checkForUpdateAction = createCheckForUpdateAction(appComponent.updater)
val gotoSettingsAction = createOpenSettingsAction(appComponent)
val perHostSettings = createPerHostSettingsPage(appComponent)
val requestExitAction = createRequestExitAction(scope, appComponent)
val startQueueGroupAction = createStartQueueGroupAction(scope, appComponent.downloadSystem.queueManager)
val stopQueueGroupAction = createStopQueueGroupAction(scope, activeQueuesFlow)
val batchDownloadAction = createOpenBatchDownloadAction(appComponent)
val openOpenSourceThirdPartyLibraries = createOpenOpenSourceThirdPartyLibrariesPage(appComponent)
