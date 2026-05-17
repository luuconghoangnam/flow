package com.flowspeed.link.android.repository

import com.flowspeed.link.android.pages.browser.BrowserActivity
import com.flowspeed.link.android.storage.AppSettingsStorage
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.autoremove.RemovedDownloadsFromDiskTracker
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.proxy.ProxyManager
import com.flowspeed.lib.downloader.DownloadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppRepository(
    scope: CoroutineScope,
    appSettings: AppSettingsStorage,
    proxyManager: ProxyManager,
    downloadSystem: DownloadSystem,
    downloadSettings: DownloadSettings,
    removedDownloadsFromDiskTracker: RemovedDownloadsFromDiskTracker,
    categoryManager: CategoryManager,
) : BaseAppRepository(
    scope = scope,
    appSettings = appSettings,
    proxyManager = proxyManager,
    downloadSystem = downloadSystem,
    downloadSettings = downloadSettings,
    removedDownloadsFromDiskTracker = removedDownloadsFromDiskTracker,
    categoryManager = categoryManager,
) {
    init {
        appSettings.browserIconInLauncher
            .debounce(500)
            .distinctUntilChanged()
            .onEach { enabled ->
                BrowserActivity.Companion.Launcher.setEnabled(enabled)
            }.launchIn(scope)
    }
}
