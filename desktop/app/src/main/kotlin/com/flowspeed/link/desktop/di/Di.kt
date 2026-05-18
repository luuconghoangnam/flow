/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.desktop.di

import com.flowspeed.link.github.GithubApi
import com.flowspeed.link.UpdateDownloadLocationProvider
import com.flowspeed.link.UpdateManager
import com.flowspeed.link.desktop.DesktopAddDownloadDialogManager
import com.flowspeed.link.desktop.AppArguments
import com.flowspeed.link.integration.IntegrationHandler
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.DesktopDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.EditDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.FileChecksumDialogManager
import com.flowspeed.link.shared.pagemanager.NotificationSender
import com.flowspeed.link.shared.pagemanager.PerHostSettingsPageManager
import com.flowspeed.link.shared.pagemanager.QueuePageManager
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.desktop.PowerActionManager
import com.flowspeed.link.desktop.actions.onevents.DesktopOnDownloadCompletionActionProvider
import com.flowspeed.link.desktop.actions.onevents.DesktopOnQueueEventActionProvider
import com.flowspeed.link.desktop.integration.IntegrationHandlerImp
import com.flowspeed.link.desktop.pages.category.DesktopCategoryDialogManager
import com.flowspeed.link.desktop.pages.settings.FontManager
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.link.desktop.repository.AppRepository
import com.flowspeed.link.desktop.storage.*
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.theme.ISystemThemeDetector
import com.flowspeed.link.desktop.utils.*
import com.flowspeed.link.desktop.utils.native_messaging.NativeMessaging
import com.flowspeed.link.desktop.utils.native_messaging.NativeMessagingManifestApplier
import com.flowspeed.link.desktop.utils.proxy.AutoConfigurableProxyProviderForDesktop
import com.flowspeed.link.desktop.utils.proxy.DesktopSystemProxySelectorProvider
import com.flowspeed.link.desktop.utils.proxy.ProxyCachingConfig
import com.flowspeed.link.desktop.utils.renderapi.CustomRenderApi
import com.flowspeed.link.integration.HLSDownloadCredentialsFromIntegration
import com.flowspeed.link.integration.HttpDownloadCredentialsFromIntegration
import com.flowspeed.link.integration.IDownloadCredentialsFromIntegration
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.flowspeed.lib.downloader.DownloadManagerMinimalControl
import com.flowspeed.lib.downloader.DownloadSettings
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.OkHttpHttpDownloaderClient
import com.flowspeed.lib.downloader.db.*
import com.flowspeed.lib.downloader.monitor.DownloadMonitor
import com.flowspeed.lib.downloader.utils.IDiskStat
import com.flowspeed.link.integration.Integration
import com.flowspeed.link.resources.FlowLanguageResources
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.downloaderinui.hls.HLSDownloaderInUi
import com.flowspeed.link.shared.downloaderinui.http.HttpDownloaderInUi
import com.flowspeed.link.shared.pagemanager.SettingsPageManager
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.ExtraQueueSettingsStorage
import com.flowspeed.link.shared.storage.IExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.IExtraQueueSettingsStorage
import com.flowspeed.link.shared.storage.PerHostSettingsDatastoreStorage
import com.flowspeed.link.shared.storage.ProxyDatastoreStorage
import com.flowspeed.link.shared.ui.theme.ThemeSettingsStorage
import com.flowspeed.link.shared.ui.widget.NotificationManager
import com.flowspeed.link.shared.updater.UpdateDownloaderViaDownloadSystem
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.DesktopDiskStat
import com.flowspeed.link.shared.util.DesktopSystemThemeDetector
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.UserAgentProviderFromSettings
import com.flowspeed.link.shared.util.*
import com.flowspeed.link.updateapplier.DesktopDirectLinkUpdateApplier
import com.flowspeed.link.updateapplier.UpdateApplier
import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.util.config.datastore.createMapConfigDatastore
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import com.flowspeed.link.updatechecker.GithubUpdateChecker
import com.flowspeed.link.updatechecker.UpdateChecker
import com.flowspeed.lib.util.AppVersionTracker
import com.flowspeed.link.shared.util.appinfo.PreviousVersion
import com.flowspeed.link.shared.util.autoremove.RemovedDownloadsFromDiskTracker
import com.flowspeed.link.shared.util.category.*
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionRunner
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueEventActionRunner
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.flowspeed.link.shared.util.perhostsettings.IPerHostSettingsStorage
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsItem
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.flowspeed.link.shared.util.ui.IMyIcons
import com.flowspeed.link.shared.util.proxy.IProxyStorage
import com.flowspeed.link.shared.util.proxy.ProxyData
import com.flowspeed.link.shared.util.proxy.ProxyManager
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.connection.UserAgentProvider
import com.flowspeed.lib.downloader.connection.proxy.AutoConfigurableProxyProvider
import com.flowspeed.lib.downloader.connection.proxy.ProxyStrategyProvider
import com.flowspeed.lib.downloader.connection.proxy.SystemProxySelectorProvider
import com.flowspeed.lib.downloader.downloaditem.DownloadJob
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloader
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadItem
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloader
import com.flowspeed.lib.downloader.monitor.DownloadItemStateFactory
import com.flowspeed.lib.downloader.monitor.IDownloadMonitor
import com.flowspeed.lib.downloader.queue.ManualDownloadQueue
import com.flowspeed.lib.downloader.utils.EmptyFileCreator
import com.flowspeed.lib.util.compose.IIconResolver
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import com.flowspeed.lib.util.compose.localizationmanager.LanguageSourceProvider
import com.flowspeed.lib.util.compose.localizationmanager.LanguageStorage
import com.flowspeed.lib.util.config.datastore.kotlinxSerializationDataStore
import com.flowspeed.lib.util.desktop.DesktopUtils
import com.flowspeed.lib.util.startup.AbstractStartupManager
import com.flowspeed.lib.util.startup.Startup
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import okhttp3.Protocol
import okhttp3.internal.tls.OkHostnameVerifier

/** Provides download engine bindings: database, HTTP client, download manager, queue manager. */
val downloaderModule = module {
    single<IDownloadQueueDatabase> {
        val definedPaths = get<DefinedPaths>()

        DownloadQueueFileStorageDatabase(
            queueFolder = get<DownloadFoldersRegistry>().registerAndGet(
                definedPaths.queuesDir
            ),
            fileSaver = get(),
        )
    }
    single<IDownloadListDb> {
        val definedPaths = get<DefinedPaths>()
        DownloadListFileStorage(
            downloadListFolder = get<DownloadFoldersRegistry>().registerAndGet(
                definedPaths.downloadListDir
            ),
            fileSaver = get(),
        )
    }
    single {
        TransactionalFileSaver(get())
    }
    single<IDownloadPartListDb> {
        val definedPaths = get<DefinedPaths>()
        PartListFileStorage(
            get<DownloadFoldersRegistry>().registerAndGet(
                definedPaths.partsDir
            ),
            get()
        )
    }
    single<IDiskStat> {
        DesktopDiskStat()
    }
    single<ISystemThemeDetector> {
        DesktopSystemThemeDetector()
    }
    single {
        QueueManager(get(), get())
    }
    single {
        DownloadFoldersRegistry()
    }
    single {
        DownloadSettings(
            8,
        )
    }
    single {
        ProxyManager(
            get()
        )
    }.bind<ProxyStrategyProvider>()
    single {
        ProxyCachingConfig.default()
    }
    single<AutoConfigurableProxyProvider> {
        AutoConfigurableProxyProviderForDesktop(get())
    }
    single<SystemProxySelectorProvider> {
        DesktopSystemProxySelectorProvider(get())
    }
    single<UserAgentProvider> {
        UserAgentProviderFromSettings(get())
    }
    single<HttpDownloaderClient> {
        OkHttpHttpDownloaderClient(
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    single {
        val downloadSettings: DownloadSettings = get()
        EmptyFileCreator(
            diskStat = get(),
            useSparseFile = { downloadSettings.useSparseFileAllocation }
        )
    }
    single {
        HLSDownloader(inject())
    }
    single {
        HLSDownloaderInUi(get(), get())
    }
    single {
        HttpDownloader(inject())
    }
    single {
        HttpDownloaderInUi(get(), get())
    }
    single {
        DownloaderInUiRegistry().apply {
            add(get<HttpDownloaderInUi>())
            add(get<HLSDownloaderInUi>())
        }
    }.bind<DownloadItemStateFactory<IDownloadItem, DownloadJob>>()
    single {
        DownloaderRegistry().apply {
            add(get<HttpDownloader>())
            add(get<HLSDownloader>())
        }
    }
    single {
        val definedPaths = get<DefinedPaths>()
        DownloadManager(
            get(),
            get(),
            get(),
            get(),
            get(),
            get<DownloadFoldersRegistry>().registerAndGet(
                definedPaths.downloadDataDir
            )
        )
    }.bind(DownloadManagerMinimalControl::class)
    single {
        ManualDownloadQueue(get(), get())
    }
    single<IDownloadMonitor> {
        DownloadMonitor(
            downloadManager = get(),
            manualDownloadQueue = get(),
            downloadItemStateFactory = inject(),
        )
    }
}
/** Provides high-level download system: DownloadSystem, category manager, settings storage, file icon provider. */
val downloadSystemModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        get<DownloadFoldersRegistry>().registerAndGet(definedPaths.categoriesDir)
        CategoryFileStorage(
            file = definedPaths.categoriesFile.toFile(),
            fileSaver = get()
        )
    }.bind<CategoryStorage>()
    single {
        FileIconProviderUsingCategoryIcons(
            get(),
            get(),
            get(),
            get(),
        )
    }.bind<FileIconProvider>()
    single {
        DefaultCategories(
            icons = get(),
            getDefaultDownloadFolder = {
                get<AppSettingsStorage>().defaultDownloadFolder.value
            }
        )
    }
    single {
        DownloadManagerCategoryItemProvider(get())
    }.bind<ICategoryItemProvider>()
    single {
        CategoryManager(
            categoryStorage = get(),
            scope = get(),
            defaultCategoriesFactory = get(),
            categoryItemProvider = get(),
        )
    }

    single {
        DownloadSystem(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    single {
        val definedPaths = get<DefinedPaths>()
        val extraDownloadSettingsStorageFolder = get<DownloadFoldersRegistry>().registerAndGet(
            definedPaths.extraDownloadSettings
        )
        ExtraDownloadSettingsStorage(
            extraDownloadSettingsStorageFolder,
            get(),
            DesktopExtraDownloadItemSettings
        )
    }.bind<IExtraDownloadSettingsStorage<*>>()
    single {
        val definedPaths = get<DefinedPaths>()
        val extraQueueSettingsStorageFolder = get<DownloadFoldersRegistry>().registerAndGet(
            definedPaths.extraQueueSettings
        )
        ExtraQueueSettingsStorage(
            extraQueueSettingsStorageFolder,
            get(),
            DesktopExtraQueueSettings
        )
    }.apply {
        bind<IExtraQueueSettingsStorage<*>>()
    }
    single<OnDownloadCompletionActionProvider> {
        DesktopOnDownloadCompletionActionProvider(get())
    }
    single<OnQueueCompletionActionProvider> {
        DesktopOnQueueEventActionProvider(get())
    }
    single {
        OnDownloadCompletionActionRunner(
            downloadManagerMinimalControl = get(),
            scope = get(),
            onDownloadCompletionActionProvider = get(),
        )
    }
    single {
        OnQueueEventActionRunner(
            queueManager = get(),
            scope = get(),
            onQueueCompletionActionProvider = get(),
        )
    }
}
/** Provides the application-wide CoroutineScope with a SupervisorJob. */
val coroutineModule = module {
    single {
        CoroutineScope(SupervisorJob())
    }
}
/** Provides the kotlinx.serialization Json instance configured for all download credential types. */
val jsonModule = module {
    single {
        val downloaderRegistry: DownloaderRegistry by inject()
        Json {
            this.encodeDefaults = true
            this.prettyPrint = true
            this.ignoreUnknownKeys = true
            this.serializersModule = SerializersModule {
                polymorphic(IDownloadItem::class) {
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadItemClass, it.downloadItemSerializer)
                    }
                    defaultDeserializer {
                        HttpDownloadItem.serializer()
                    }
                }
                polymorphic(IDownloadCredentials::class) {
                    downloaderRegistry.getAll().forEach {
                        subclass(it.downloadCredentialsClass, it.downloadCredentialsSerializer)
                    }
                    defaultDeserializer {
                        HttpDownloadCredentials.serializer()
                    }
                }
                // Legacy polymorphic registration kept for backward compatibility with older stored data.
                polymorphic(IDownloadCredentialsFromIntegration::class) {
                    subclass(
                        HttpDownloadCredentialsFromIntegration::class,
                        HttpDownloadCredentialsFromIntegration.serializer()
                    )
                    subclass(
                        HLSDownloadCredentialsFromIntegration::class,
                        HLSDownloadCredentialsFromIntegration.serializer()
                    )
                    defaultDeserializer {
                        HttpDownloadCredentialsFromIntegration.serializer()
                    }
                }
            }
        }
    }
}
/** Provides the browser integration HTTP server and its handler. */
val integrationModule = module {
    single<IntegrationHandler> {
        IntegrationHandlerImp()
    }
    single {
        Integration(get(), get(), get(), AppInfo.isInDebugMode())
    }
}
/** Provides the auto-update checker and applier. */
val updaterModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        UpdateDownloadLocationProvider {
            definedPaths.updateDownloadLocation.toFile()
        }
    }
    single<UpdateApplier> {
        val definedPaths = get<DefinedPaths>()
        definedPaths.updateDownloadLocation
        DesktopDirectLinkUpdateApplier(
            installationFolder = AppInfo.installationFolder,
            updateFolder = definedPaths.updateDir.toString(),
            logDir = definedPaths.logDir.toString(),
            appName = AppInfo.name,
            updatePreparer = UpdateDownloaderViaDownloadSystem(
                get(),
                get(),
            ),
        )
    }
    single<UpdateChecker> {
        GithubUpdateChecker(
            AppVersion.get(),
            githubApi = GithubApi(
                owner = SharedConstants.projectGithubOwner,
                repo = SharedConstants.projectGithubRepo,
                client = OkHttpClient
                    .Builder()
                    .build()
            )
        )
    }
    single {
        UpdateManager(
            updateChecker = get(),
            updateApplier = get(),
            appVersionTracker = get(),
        )
    }
}
/** Provides the OS startup manager (registers app in system autostart). */
val startUpModule = module {
    single {
        Startup.getStartUpManagerForDesktop(
            name = AppInfo.displayName,
            path = AppInfo.exeFile,
            args = listOf(AppArguments.Args.BACKGROUND),
            packageName = AppInfo.packageName,
        )
    }.apply {
        bind<AbstractStartupManager>()
    }
}
/** Provides native messaging support for browser extension communication. */
val nativeMessagingModule = module {
    single<NativeMessaging> {
        NativeMessaging(NativeMessagingManifestApplier.getForCurrentPlatform())
    }
}

val appModule = module {
    includes(downloaderModule)
    includes(downloadSystemModule)
    includes(coroutineModule)
    includes(jsonModule)
    includes(integrationModule)
    includes(updaterModule)
    includes(startUpModule)
    includes(nativeMessagingModule)
//    single {
//        NetworkChecker(get())
//    }
    single {
        AppInfo.definedPaths
    }.bind<DefinedPaths>()
    single {
        AppRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }.apply {
        bind<BaseAppRepository>()
        bind<SizeAndSpeedUnitProvider>()
    }
    single {
        ThemeManager(get(), get(), get())
    }
    single {
        FontManager(get())
    }
    single {
        LanguageManager(
            get(),
            LanguageSourceProvider(
                FlowLanguageResources.defaultLanguageResource,
                FlowLanguageResources.languages,
            )
        )
    }
    single {
        MyIcons
    }.apply {
        bind<IMyIcons>()
        bind<IIconResolver>()
    }
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
            createMapConfigDatastore(
                definedPaths.appSettingsFile.toFile(),
                get(),
            )
        )
    }.apply {
        bind<BaseAppSettingsStorage>()
        bind<LanguageStorage>()
        bind<ThemeSettingsStorage>()
    }
    single {
        val definedPaths = get<DesktopDefinedPaths>()
        PageStatesStorage(
            createMapConfigDatastore(
                definedPaths.pageStatesStorageFile.toFile(),
                get(),
            )
        )
    }
    single {
        val lifecycle = LifecycleRegistry(
            Lifecycle.State.RESUMED
        )
        val context = DefaultComponentContext(lifecycle)
        runBlocking {
            withContext(Dispatchers.Main) {
                AppComponent(context)
            }
        }
    }.apply {
        bind<DesktopDownloadDialogManager>()
        bind<DesktopAddDownloadDialogManager>()
        bind<DesktopCategoryDialogManager>()
        bind<EditDownloadDialogManager>()
        bind<FileChecksumDialogManager>()
        bind<QueuePageManager>()
        bind<NotificationSender>()
        bind<DownloadItemOpener>()
        bind<PerHostSettingsPageManager>()
        bind<PowerActionManager>()
        bind<SettingsPageManager>()
    }
    single {
        RemovedDownloadsFromDiskTracker(
            get(), get(), get(),
        )
    }
    single {
        val definedPaths = get<DefinedPaths>()
        PreviousVersion(
            systemPath = definedPaths.systemDir.toFile(),
            currentVersion = AppInfo.version,
        )
    }
    single {
        AppVersionTracker(
            previousVersion = {
                // it MUST be booted first
                get<PreviousVersion>().get()
            },
            currentVersion = AppInfo.version,
        )
    }

    single {
        val appSettingsStorage: AppSettingsStorage = get()
        AppSSLFactoryProvider(
            ignoreSSLCertificates = appSettingsStorage.ignoreSSLCertificates
        )
    }
    single {
        val appSettingsStorage: AppSettingsStorage = get()
        AppHostNameVerifier(
            delegateHostnameVerifier = OkHostnameVerifier,
            ignoreHostNameVerification = appSettingsStorage.ignoreSSLCertificates
        )
    }
    single<OkHttpClient> {
        val appSSLFactoryProvider: AppSSLFactoryProvider = get()
        val appHostNameVerifier: AppHostNameVerifier = get()
        OkHttpClient
            .Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .dispatcher(Dispatcher().apply {
                //bypass limit on concurrent connections!
                maxRequests = Int.MAX_VALUE
                maxRequestsPerHost = Int.MAX_VALUE
            })
            .sslSocketFactory(
                appSSLFactoryProvider.createSSLSocketFactory(),
                appSSLFactoryProvider.trustManager,
            )
            .hostnameVerifier(appHostNameVerifier)
            .build()
    }
    single {
        KeepAwakeManager(
            DesktopUtils.keepAwakeService(),
            get(),
            get(),
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
    single {
        PerHostSettingsManager(get())
    }
    single { NotificationManager() }

    single {
        val definedPaths = get<DesktopDefinedPaths>()
        CustomRenderApi(definedPaths.renderApiFile)
    }
}


object Di : KoinComponent {
    fun boot() {
        startKoin {
            modules(appModule)
        }
    }
}
