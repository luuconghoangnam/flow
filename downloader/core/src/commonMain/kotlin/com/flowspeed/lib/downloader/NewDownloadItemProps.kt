package com.flowspeed.lib.downloader

import com.flowspeed.lib.downloader.downloaditem.DownloadItemContext
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.utils.OnDuplicateStrategy

data class NewDownloadItemProps(
    val downloadItem: IDownloadItem,
    val extraConfig: DownloadJobExtraConfig?,
    val onDuplicateStrategy: OnDuplicateStrategy,
    val context: DownloadItemContext,
)
