package com.flowspeed.link.android.pages.browser

import android.webkit.CookieManager
import com.flowspeed.link.android.ui.widget.WebViewState
import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.util.HttpUrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias FlowWebRequestId = String

data class FlowWebRequest(
    val url: String,
    val headers: Map<String, String>,
    val page: String?,
) {
    val id: FlowWebRequestId = url
}

interface RequestInterceptor {
    fun interceptRequest(request: FlowWebRequest)
}

class DownloadInterceptor(
    private val scope: CoroutineScope,
    private val onNewDownload: (newDownloads: List<AddDownloadCredentialsInUiProps>) -> Unit,
) : RequestInterceptor {
    private val requests = mutableMapOf<String, FlowWebRequest>()

    fun onDownloadStart(
        url: String?,
        userAgent: String?,
        page: String?,
        tab: FlowBrowserTab,
    ) {
        if (url == null) {
            return
        }
        if (!HttpUrlUtils.isValidUrl(url)) {
            return
        }
        val webRequest = getWebRequestOrDefault(
            url = url,
            userAgent = userAgent,
            page = page,
            webViewState = tab.tabState,
        )
        onNewDownload(
            listOf(
                AddDownloadCredentialsInUiProps(
                    HttpDownloadCredentials(
                        link = webRequest.url,
                        headers = webRequest.headers,
                        downloadPage = webRequest.page,
                    ),
                    AddDownloadCredentialsInUiProps.Configs()
                )
            )
        )
    }

    override fun interceptRequest(
        request: FlowWebRequest,
    ) {
        addToHeaders(request)
    }

    private fun addToHeaders(request: FlowWebRequest) {
        requests[request.id] = request
        scope.launch {
            delay(REMOVE_REQUESTS_DELAY)
            requests.remove(request.id)
        }
    }

    private fun getWebRequestOrDefault(
        url: String,
        userAgent: String?,
        page: String?,
        webViewState: WebViewState,
    ): FlowWebRequest {
        var request = requests[url]
        if (request == null) {
            request = FlowWebRequest(
                url = url,
                headers = emptyMap(),
                page = getPageUrl(webViewState) ?: page,
            )
        }
        return request
            .withUserAgent(userAgent)
            .withCookieManagerCookies()
    }

    private fun FlowWebRequest.withUserAgent(userAgent: String?): FlowWebRequest {
        val request = this
        if (userAgent == null) {
            return request
        }
        val userAgentKey = "User-Agent"
        if (request.headers.containsKey(userAgentKey)) {
            return request
        }
        return request.copy(
            headers = request.headers.plus(
                userAgentKey to userAgent
            )
        )
    }

    private fun FlowWebRequest.withCookieManagerCookies(): FlowWebRequest {
        val request = this
        val cookieFromCookieManager =
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() } ?: return request
        val cookieKey = "Cookie"
        val currentCookie = request.headers[cookieKey]?.takeIf { it.isNotBlank() }
        return request.copy(
            headers = request.headers.plus(
                cookieKey to if (currentCookie != null) {
                    "$currentCookie; $cookieFromCookieManager"
                } else {
                    cookieFromCookieManager
                }
            )
        )
    }

    private fun getPageUrl(state: WebViewState): String? {
        return state.lastLoadedUrl
    }

    companion object {
        private const val REMOVE_REQUESTS_DELAY = 20_000L
    }
}
