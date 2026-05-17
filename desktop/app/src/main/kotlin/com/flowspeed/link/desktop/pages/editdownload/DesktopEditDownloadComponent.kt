package com.flowspeed.link.desktop.pages.editdownload

import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.pages.editdownload.BaseEditDownloadComponent
import com.flowspeed.link.shared.util.mvi.ContainsEffects
import com.flowspeed.link.shared.util.mvi.supportEffects
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.FileIconProvider
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent

sealed interface EditDownloadPageEffects {
    data object BringToFront : EditDownloadPageEffects
}

class DesktopEditDownloadComponent(
    ctx: ComponentContext,
    onRequestClose: () -> Unit,
    downloadId: Long,
    acceptEdit: StateFlow<Boolean>,
    onEdited: ((IDownloadItem) -> Unit, DownloadJobExtraConfig?) -> Unit,
    downloadSystem: DownloadSystem,
    downloaderInUiRegistry: DownloaderInUiRegistry,
    iconProvider: FileIconProvider,
) : BaseEditDownloadComponent(
    ctx = ctx,
    downloadSystem = downloadSystem,
    downloaderInUiRegistry = downloaderInUiRegistry,
    iconProvider = iconProvider,
    onEdited = onEdited,
    onRequestClose = onRequestClose,
    downloadId = downloadId,
    acceptEdit = acceptEdit,
),
    ContainsEffects<EditDownloadPageEffects> by supportEffects(),
    KoinComponent {
    fun bringToFront() {
        sendEffect(EditDownloadPageEffects.BringToFront)
    }
}
