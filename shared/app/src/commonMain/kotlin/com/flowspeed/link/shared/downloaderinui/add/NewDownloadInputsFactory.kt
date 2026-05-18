package com.flowspeed.link.shared.downloaderinui.add

import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.downloaderinui.LinkChecker
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.lib.downloader.connection.IResponseInfo
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.CoroutineScope

interface NewDownloadInputsFactory<
        TDownloadItem : IDownloadItem,
        TCredentials : IDownloadCredentials,
        TResponseInfoType : IResponseInfo,
        TDownloadSize : DownloadSize,
        TLinkChecker : LinkChecker<TCredentials, TResponseInfoType, TDownloadSize>,
        TNewDownloadInputs : NewDownloadInputs<
                TDownloadItem,
                TCredentials,
                TResponseInfoType,
                TDownloadSize,
                TLinkChecker,
                >
        > {
    fun createNewDownloadInputs(
        initialCredentials: TCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ): TNewDownloadInputs
}
