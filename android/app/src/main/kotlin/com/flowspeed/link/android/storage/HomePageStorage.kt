package com.flowspeed.link.android.storage

import androidx.datastore.core.DataStore
import com.flowspeed.link.android.pages.home.HomePageStateToPersist
import com.flowspeed.link.android.pages.home.sortBy
import com.flowspeed.link.shared.util.ConfigBaseSettingsByJson

class HomePageStorage(
    dataStore: DataStore<HomePageStateToPersist>,
) : ConfigBaseSettingsByJson<HomePageStateToPersist>(
    dataStore = dataStore,
) {
    val sortBy = from(HomePageStateToPersist.sortBy)
}
