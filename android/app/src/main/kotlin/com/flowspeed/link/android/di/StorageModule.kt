package com.flowspeed.link.android.di

import com.flowspeed.lib.util.compose.localizationmanager.LanguageStorage
import com.flowspeed.lib.util.config.datastore.createMapConfigDatastore
import com.flowspeed.lib.util.config.datastore.kotlinxSerializationDataStore
import com.flowspeed.link.android.pages.home.HomePageStateToPersist
import com.flowspeed.link.android.storage.AndroidOnBoardingStorage
import com.flowspeed.link.android.storage.AppSettingsStorage
import com.flowspeed.link.android.storage.BrowserBookmarksStorage
import com.flowspeed.link.android.storage.HomePageStorage
import com.flowspeed.link.android.storage.OnBoardingData
import com.flowspeed.link.android.util.AndroidDefinedPaths
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ILastSavedLocationsStorage
import com.flowspeed.link.shared.storage.PerHostSettingsDatastoreStorage
import com.flowspeed.link.shared.storage.ProxyDatastoreStorage
import com.flowspeed.link.shared.storage.impl.LastSavedLocationStorage
import com.flowspeed.link.shared.ui.theme.ThemeSettingsStorage
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.perhostsettings.IPerHostSettingsStorage
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsItem
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.flowspeed.link.shared.util.proxy.IProxyStorage
import com.flowspeed.link.shared.util.proxy.ProxyData
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Storage & settings DI module (Android).
 *
 * Provides: app settings datastore, proxy storage, per-host settings,
 * onboarding storage, home page state, and browser bookmarks.
 */
val storageModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        ProxyDatastoreStorage(
            kotlinxSerializationDataStore(
                definedPaths.proxySettingsFile.toFile(),
                get(),
                ProxyData::default,
            )
        )
    }.bind<IProxyStorage>()
    single {
        val definedPaths = get<DefinedPaths>()
        AppSettingsStorage(
            createMapConfigDatastore(definedPaths.appSettingsFile.toFile(), get())
        )
    }.apply {
        bind<BaseAppSettingsStorage>()
        bind<LanguageStorage>()
        bind<ThemeSettingsStorage>()
    }
    single<ILastSavedLocationsStorage> {
        val definedPaths = get<AndroidDefinedPaths>()
        LastSavedLocationStorage(
            kotlinxSerializationDataStore<List<String>>(
                definedPaths.lastSavedLocationFile.toFile(),
                get(),
                ::emptyList,
            )
        )
    }
    single<IPerHostSettingsStorage> {
        val definedPaths = get<DefinedPaths>()
        PerHostSettingsDatastoreStorage(
            kotlinxSerializationDataStore<List<PerHostSettingsItem>>(
                definedPaths.perHostSettingsFile.toFile(),
                get(),
                ::emptyList,
            )
        )
    }
    single { PerHostSettingsManager(get()) }
    single {
        val paths = get<AndroidDefinedPaths>()
        AndroidOnBoardingStorage(
            kotlinxSerializationDataStore(
                paths.onboardingFile.toFile(),
                get(),
                ::OnBoardingData,
            )
        )
    }
    single {
        val paths = get<AndroidDefinedPaths>()
        HomePageStorage(
            kotlinxSerializationDataStore(
                paths.homePageFile.toFile(),
                get(),
                ::HomePageStateToPersist,
            )
        )
    }
    single {
        val paths = get<AndroidDefinedPaths>()
        BrowserBookmarksStorage(
            kotlinxSerializationDataStore(
                paths.browserBookmarksFile.toFile(),
                get(),
                ::emptyList,
            )
        )
    }
}
