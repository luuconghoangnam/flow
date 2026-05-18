package com.flowspeed.link.desktop.di

import com.flowspeed.lib.util.compose.localizationmanager.LanguageStorage
import com.flowspeed.lib.util.config.datastore.createMapConfigDatastore
import com.flowspeed.lib.util.config.datastore.kotlinxSerializationDataStore
import com.flowspeed.link.desktop.storage.AppSettingsStorage
import com.flowspeed.link.desktop.storage.DesktopDefinedPaths
import com.flowspeed.link.desktop.storage.PageStatesStorage
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.PerHostSettingsDatastoreStorage
import com.flowspeed.link.shared.storage.ProxyDatastoreStorage
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
 * Storage & settings DI module.
 *
 * Provides: app settings datastore, proxy storage,
 * per-host settings, and page state persistence.
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
    single {
        val definedPaths = get<DesktopDefinedPaths>()
        PageStatesStorage(
            createMapConfigDatastore(definedPaths.pageStatesStorageFile.toFile(), get())
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
}
