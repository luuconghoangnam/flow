package com.flowspeed.link.shared.downloaderinui.hls.add

import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.downloaderinui.DownloadUiChecker
import com.flowspeed.link.shared.downloaderinui.LinkCheckerFactory
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadCredentials
import com.flowspeed.link.shared.downloaderinui.hls.HLSLinkChecker
import com.flowspeed.lib.downloader.downloaditem.hls.HLSResponseInfo
import com.flowspeed.link.shared.util.DownloadSystem
import kotlinx.coroutines.CoroutineScope

class HLSDownloadUIChecker(
    initCredentials: HLSDownloadCredentials,
    linkCheckerFactory: LinkCheckerFactory<HLSDownloadCredentials, HLSResponseInfo, DownloadSize.Duration, HLSLinkChecker>,
    initialFolder: String,
    initialName: String,
    downloadSystem: DownloadSystem,
    scope: CoroutineScope,
) : DownloadUiChecker<HLSDownloadCredentials, HLSResponseInfo, DownloadSize.Duration, HLSLinkChecker>(
    initialCredentials = initCredentials,
    linkCheckerFactory = linkCheckerFactory,
    initialFolder = initialFolder,
    initialName = initialName,
    downloadSystem = downloadSystem,
    scope = scope,
) {
}
