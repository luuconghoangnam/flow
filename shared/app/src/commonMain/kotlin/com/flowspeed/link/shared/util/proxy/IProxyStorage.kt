package com.flowspeed.link.shared.util.proxy

import kotlinx.coroutines.flow.MutableStateFlow

interface IProxyStorage {
    val proxyDataFlow: MutableStateFlow<ProxyData>
}
