package com.flowspeed.link.shared.storage

import androidx.datastore.core.DataStore
import com.flowspeed.link.shared.util.ConfigBaseSettingsByJson
import com.flowspeed.link.shared.util.proxy.IProxyStorage
import com.flowspeed.link.shared.util.proxy.ProxyData

class ProxyDatastoreStorage(
    dataStore: DataStore<ProxyData>,
) : IProxyStorage, ConfigBaseSettingsByJson<ProxyData>(dataStore) {
    override val proxyDataFlow = data
}
