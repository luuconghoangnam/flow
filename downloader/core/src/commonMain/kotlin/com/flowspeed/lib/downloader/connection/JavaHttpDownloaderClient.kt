package com.flowspeed.lib.downloader.connection

import com.flowspeed.lib.downloader.connection.proxy.*
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.IHttpDownloadCredentials
import okio.Source
import okio.source
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * HTTP client implementation using Java 11+ built-in `java.net.http.HttpClient`.
 *
 * Designed for GraalVM native-image compatibility (no OkHttp dependency).
 * Supports: Range headers, proxy, custom headers, Basic Auth, SSL bypass.
 *
 * Thread-safe: HttpClient instances are cached per proxy configuration.
 */
class JavaHttpDownloaderClient(
    private val customUserAgentProvider: UserAgentProvider,
    private val proxyStrategyProvider: ProxyStrategyProvider,
    private val systemProxySelectorProvider: SystemProxySelectorProvider,
    private val autoConfigurableProxyProvider: AutoConfigurableProxyProvider,
    private val ignoreSSL: () -> Boolean = { false },
) : HttpDownloaderClient() {

    /** Cache proxy-configured clients to avoid creating new instances per connection. */
    private val clientCache = ConcurrentHashMap<ProxyStrategy, HttpClient>()

    private fun getClient(credentials: IHttpBasedDownloadCredentials): HttpClient {
        val strategy = proxyStrategyProvider.getProxyStrategyFor(credentials.link)
        return when (strategy) {
            ProxyStrategy.Direct -> directClient
            else -> clientCache.getOrPut(strategy) { buildClient(strategy) }
        }
    }

    private val directClient: HttpClient by lazy { buildClient(ProxyStrategy.Direct) }

    private fun buildClient(strategy: ProxyStrategy): HttpClient {
        val builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)

        // SSL configuration
        if (ignoreSSL()) {
            builder.sslContext(createTrustAllSSLContext())
        }

        // Proxy configuration
        when (strategy) {
            ProxyStrategy.Direct -> {} // no proxy
            ProxyStrategy.UseSystem -> {
                val selector = systemProxySelectorProvider.getSystemProxySelector()
                if (selector != null) builder.proxy(selector)
            }
            is ProxyStrategy.ByScript -> {
                val selector = autoConfigurableProxyProvider.getAutoConfigurableProxy(strategy.scriptPath)
                if (selector != null) builder.proxy(selector)
            }
            is ProxyStrategy.ManualProxy -> {
                val proxy = strategy.proxy
                val javaProxy = java.net.Proxy(
                    when (proxy.type) {
                        ProxyType.HTTP -> java.net.Proxy.Type.HTTP
                        ProxyType.SOCKS -> java.net.Proxy.Type.SOCKS
                    },
                    InetSocketAddress(proxy.host, proxy.port)
                )
                builder.proxy(ProxySelector.of(InetSocketAddress(proxy.host, proxy.port)))
                if (proxy.username != null) {
                    builder.authenticator(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(proxy.username, proxy.password?.toCharArray() ?: charArrayOf())
                        }
                    })
                }
            }
        }

        return builder.build()
    }

    private fun buildRequest(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
        method: String = "GET",
    ): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(credentials.link))

        // Default headers (first, can be overridden)
        defaultHeadersInFirst().forEach { (k, v) -> builder.header(k, v) }

        // User-Agent
        val userAgent = credentials.userAgent
            ?: customUserAgentProvider.getUserAgent()
            ?: getDefaultUserAgent()
        builder.header("User-Agent", userAgent)

        // Custom headers from credentials
        credentials.headers
            ?.filter { !it.key.equals("Host", true) }
            ?.forEach { (k, v) -> builder.header(k, v) }

        // Default headers (last, override previous)
        defaultHeadersInLast().forEach { (k, v) -> builder.header(k, v) }

        // Basic Auth
        val username = credentials.username
        val password = credentials.password
        if (username?.isNotBlank() == true && password?.isNotBlank() == true) {
            val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            builder.header("Authorization", "Basic $encoded")
        }

        // Range header
        if (start != null) {
            val rangeValue = "bytes=$start-${end ?: ""}"
            builder.header("Range", rangeValue)
        }

        // Method
        when (method) {
            "GET" -> builder.GET()
            "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody())
        }

        return builder.build()
    }

    override suspend fun actualHead(
        credentials: IHttpDownloadCredentials,
        start: Long?,
        end: Long?,
    ): HttpResponseInfo {
        val client = getClient(credentials)
        val request = buildRequest(credentials, start, end, method = "GET")
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return createResponseInfo(request, response)
    }

    override suspend fun actualConnect(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
    ): Connection<HttpResponseInfo> {
        val client = getClient(credentials)
        val request = buildRequest(credentials, start, end, method = "GET")
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        val body = response.body()
        val contentLength = response.headers()
            .firstValueAsLong("content-length")
            .orElse(-1L)

        val source: Source = body.source()

        return Connection(
            source = source,
            contentLength = contentLength,
            responseInfo = createResponseInfo(request, response),
        )
    }

    private fun createResponseInfo(request: HttpRequest, response: HttpResponse<*>): HttpResponseInfo {
        val responseHeaders = linkedMapOf<String, String>()
        response.headers().map().forEach { (key, values) ->
            // Take last value for each header (matches OkHttp behavior)
            values.lastOrNull()?.let { responseHeaders[key.lowercase()] = it }
        }

        val requestHeaders = linkedMapOf<String, String>()
        request.headers().map().forEach { (key, values) ->
            values.lastOrNull()?.let { requestHeaders[key.lowercase()] = it }
        }

        return HttpResponseInfo(
            statusCode = response.statusCode(),
            message = "", // Java HttpClient doesn't expose reason phrase
            requestUrl = request.uri().toString(),
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
        )
    }

    private fun createTrustAllSSLContext(): SSLContext {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        return SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
    }
}
