package com.flowspeed.link.shared.downloaderinui.http

import com.flowspeed.link.shared.downloaderinui.CredentialAndItemMapper
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadItem
import com.flowspeed.lib.downloader.downloaditem.http.withHttpCredentials

object HttpCredentialsToItemMapper : CredentialAndItemMapper<HttpDownloadCredentials, HttpDownloadItem> {
    override fun itemToCredentials(item: HttpDownloadItem): HttpDownloadCredentials {
        return HttpDownloadCredentials.from(item)
    }

    override fun appliedCredentialsToItem(
        item: HttpDownloadItem,
        credentials: HttpDownloadCredentials
    ): HttpDownloadItem {
        return item.copy().withHttpCredentials(credentials)
    }

    override fun itemWithEditedName(item: HttpDownloadItem, name: String): HttpDownloadItem {
        return item.copy(name = name)
    }

    override fun credentialsWithEditedLink(
        credentials: HttpDownloadCredentials,
        link: String
    ): HttpDownloadCredentials {
        return credentials.copy(link = link)
    }

}
