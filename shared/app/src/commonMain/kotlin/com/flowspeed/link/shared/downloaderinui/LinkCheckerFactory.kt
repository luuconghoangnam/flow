package com.flowspeed.link.shared.downloaderinui

import com.flowspeed.lib.downloader.connection.IResponseInfo
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials

interface LinkCheckerFactory<
        TCredentials : IDownloadCredentials,
        TResponseInfo : IResponseInfo,
        TDownloadSize : DownloadSize,
        TLinkChecker : LinkChecker<TCredentials, TResponseInfo, TDownloadSize>,
        > {
    fun createLinkChecker(
        initialCredentials: TCredentials
    ): TLinkChecker
}
