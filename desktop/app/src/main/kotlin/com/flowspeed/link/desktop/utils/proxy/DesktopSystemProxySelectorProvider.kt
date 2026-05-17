package com.flowspeed.link.desktop.utils.proxy

import com.github.markusbernhardt.proxy.ProxySearch
import com.flowspeed.lib.downloader.connection.proxy.SystemProxySelectorProvider
import java.net.ProxySelector

class DesktopSystemProxySelectorProvider(
    private val proxyCachingConfig: ProxyCachingConfig
) : SystemProxySelectorProvider {
    private val proxySearch by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createProxySearch()
    }

    private fun createProxySearch(): ProxySearch {
        return ProxySearch.getDefaultProxySearch().apply {
            setPacCacheSettings(
                proxyCachingConfig.pacCacheSize,
                proxyCachingConfig.pacCacheTTL,
                proxyCachingConfig.pacCacheScope
            )
        }
    }

    override fun getSystemProxySelector(): ProxySelector? {
        return proxySearch.proxySelector
    }
}
