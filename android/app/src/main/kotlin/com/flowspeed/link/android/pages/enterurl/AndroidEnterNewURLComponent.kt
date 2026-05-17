package com.flowspeed.link.android.pages.enterurl

import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.pages.enterurl.BaseEnterNewURLComponent
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials

class AndroidEnterNewURLComponent(
    ctx: ComponentContext,
    config: AndroidEnterNewURLComponent.Config,
    downloaderInUiRegistry: DownloaderInUiRegistry,
    onCloseRequest: () -> Unit,
    onRequestFinished: (IDownloadCredentials) -> Unit,
) : BaseEnterNewURLComponent(
    ctx = ctx,
    config = config,
    downloaderInUiRegistry = downloaderInUiRegistry,
    onCloseRequest = onCloseRequest,
    onRequestFinished = onRequestFinished,
) {
    object Config : BaseEnterNewURLComponent.Config

    override val shouldFillWithClipboard: Boolean = false
}
