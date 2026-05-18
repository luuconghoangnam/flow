package com.flowspeed.link.shared.downloaderinui.http.add

import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.downloaderinui.DownloadUiChecker
import com.flowspeed.link.shared.downloaderinui.LinkCheckerFactory
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import kotlinx.coroutines.CoroutineScope

class HttpDownloadUiChecker(
    initialCredentials: HttpDownloadCredentials = HttpDownloadCredentials.Companion.empty(),
    linkCheckerFactory: LinkCheckerFactory<HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes, HttpLinkChecker>,
    initialFolder: String,
    initialName: String = "",
    downloadSystem: DownloadSystem,
    scope: CoroutineScope,
) : DownloadUiChecker<HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes, HttpLinkChecker>(
    initialCredentials, linkCheckerFactory, initialFolder, initialName, downloadSystem, scope
) {
}
