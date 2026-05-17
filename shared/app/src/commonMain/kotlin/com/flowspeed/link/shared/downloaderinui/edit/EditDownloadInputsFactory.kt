package com.flowspeed.link.shared.downloaderinui.edit

import com.flowspeed.link.shared.downloaderinui.CredentialAndItemMapper
import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.downloaderinui.LinkChecker
import com.flowspeed.lib.downloader.connection.IResponseInfo
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

interface EditDownloadInputsFactory<
        TDownloadItem : IDownloadItem,
        TCredentials : IDownloadCredentials,
        TResponseInfo : IResponseInfo,
        TDownloadSize : DownloadSize,
        TLinkChecker : LinkChecker<TCredentials, TResponseInfo, TDownloadSize>,
        TCredentialsToItemMapper : CredentialAndItemMapper<TCredentials, TDownloadItem>,
        TEditDownloadInputs : EditDownloadInputs<TDownloadItem, TCredentials, TResponseInfo, TDownloadSize, TLinkChecker, TCredentialsToItemMapper>
        > {
    fun createEditDownloadInputs(
        currentDownloadItem: MutableStateFlow<TDownloadItem>,
        editedDownloadItem: MutableStateFlow<TDownloadItem>,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope,
    ): TEditDownloadInputs
}


