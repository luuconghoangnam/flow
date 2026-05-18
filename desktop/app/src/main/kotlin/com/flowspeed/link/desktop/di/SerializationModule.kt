package com.flowspeed.link.desktop.di

import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadItem
import com.flowspeed.link.integration.HLSDownloadCredentialsFromIntegration
import com.flowspeed.link.integration.HttpDownloadCredentialsFromIntegration
import com.flowspeed.link.integration.IDownloadCredentialsFromIntegration
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.core.component.inject
import org.koin.dsl.module

/**
 * Serialization DI module.
 *
 * Provides: kotlinx.serialization Json instance configured with
 * polymorphic serializers for all download item and credential types.
 */
val serializationModule = module {
    single {
        val downloaderRegistry: DownloaderRegistry by inject()
        Json {
            encodeDefaults = true
            prettyPrint = true
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                polymorphic(IDownloadItem::class) {
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadItemClass, it.downloadItemSerializer)
                    }
                    defaultDeserializer { HttpDownloadItem.serializer() }
                }
                polymorphic(IDownloadCredentials::class) {
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadCredentialsClass, it.downloadCredentialsSerializer)
                    }
                    defaultDeserializer { HttpDownloadCredentials.serializer() }
                }
                // Legacy polymorphic registration for backward compatibility with older stored data.
                polymorphic(IDownloadCredentialsFromIntegration::class) {
                    subclass(HttpDownloadCredentialsFromIntegration::class, HttpDownloadCredentialsFromIntegration.serializer())
                    subclass(HLSDownloadCredentialsFromIntegration::class, HLSDownloadCredentialsFromIntegration.serializer())
                    defaultDeserializer { HttpDownloadCredentialsFromIntegration.serializer() }
                }
            }
        }
    }
}
