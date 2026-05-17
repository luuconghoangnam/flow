package com.flowspeed.lib.downloader.connection.proxy

interface ProxyStrategyProvider {
    fun getProxyStrategyFor(url: String): ProxyStrategy
}
