package com.flowspeed.link.shared.downloaderinui.http.add

import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.util.FilenameFixer
import com.flowspeed.link.shared.downloaderinui.LinkChecker
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.util.HttpUrlUtils
import com.flowspeed.lib.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HttpLinkChecker(
    initialCredentials: HttpDownloadCredentials = HttpDownloadCredentials.empty(),
    private val client: HttpDownloaderClient,
) : LinkChecker<HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes>(initialCredentials) {
    private val _suggestedName = MutableStateFlow(null as String?)
    override val suggestedName = _suggestedName.asStateFlow()

    private val _length = MutableStateFlow(null as Long?)
    override val downloadSize = _length.mapStateFlow {
        it?.let(DownloadSize::Bytes)
    }

    override fun infoUpdated(responseInfo: HttpResponseInfo?) {
        updateNameAndLength(responseInfo)
    }

    override suspend fun actualCheck(credentials: HttpDownloadCredentials): HttpResponseInfo {
        return client.test(credentials)
    }

    private fun updateNameAndLength(responseInfo: HttpResponseInfo?) {
        val suggestedName = responseInfo
            ?.fileName ?: HttpUrlUtils.extractNameFromLink(credentials.value.link)
            ?.let(FilenameFixer::fix)
        val length = responseInfo?.run {
            totalLength.takeIf { isSuccessFul }
        }
        _suggestedName.update { suggestedName }
        _length.update { length }
    }
}
