package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.Downloader
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

class HttpDownloader(
    httpDownloaderClient: Lazy<HttpDownloaderClient>
) : Downloader<HttpDownloadItem, HttpDownloadJob, HttpDownloadCredentials> {
    val httpDownloaderClient by httpDownloaderClient
    override fun createJob(
        item: HttpDownloadItem,
        downloadManager: DownloadManager,
    ): HttpDownloadJob {
        return HttpDownloadJob(
            item,
            downloadManager,
            httpDownloaderClient,
        )
    }

    override fun accept(item: IDownloadItem): Boolean {
        return item is HttpDownloadItem
    }

    override val downloadItemClass: KClass<HttpDownloadItem> = HttpDownloadItem::class
    override val downloadCredentialsClass: KClass<HttpDownloadCredentials> = HttpDownloadCredentials::class
    override val downloadJobClass: KClass<HttpDownloadJob> = HttpDownloadJob::class
    override val downloadItemSerializer: KSerializer<HttpDownloadItem> = HttpDownloadItem.serializer()
    override val downloadCredentialsSerializer: KSerializer<HttpDownloadCredentials> =
        HttpDownloadCredentials.serializer()
}
