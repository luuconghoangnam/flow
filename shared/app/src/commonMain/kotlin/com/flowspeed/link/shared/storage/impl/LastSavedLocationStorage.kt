package com.flowspeed.link.shared.storage.impl

import androidx.datastore.core.DataStore
import com.flowspeed.link.shared.storage.ILastSavedLocationsStorage
import com.flowspeed.link.shared.util.ConfigBaseSettingsByJson
import kotlinx.coroutines.flow.MutableStateFlow

class LastSavedLocationStorage(
    dataStore: DataStore<List<String>>
) : ConfigBaseSettingsByJson<List<String>>(dataStore), ILastSavedLocationsStorage {
    override val lastUsedSaveLocations: MutableStateFlow<List<String>> = data
}
