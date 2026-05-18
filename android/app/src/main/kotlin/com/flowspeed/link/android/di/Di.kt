package com.flowspeed.link.android.di

import AndroidDirectLinkUpdateApplier
import android.app.Application
import android.content.Context
import com.flowspeed.link.github.GithubApi
import com.flowspeed.link.UpdateDownloadLocationProvider
import com.flowspeed.link.UpdateManager
import com.flowspeed.link.android.FlowApp
import com.flowspeed.link.android.pages.home.HomePageStateToPersist
import com.flowspeed.link.android.pages.onboarding.permissions.FlowPermissions
import com.flowspeed.link.android.pages.onboarding.permissions.PermissionManager
import com.flowspeed.link.android.receiver.StartOnBootBroadcastReceiver
import com.flowspeed.link.android.repository.AppRepository
import com.flowspeed.link.android.storage.AndroidExtraDownloadItemSettings
import com.flowspeed.link.android.storage.AndroidExtraQueueSettings
import com.flowspeed.link.android.storage.AndroidOnBoardingStorage
import com.flowspeed.link.android.storage.AppSettingsStorage
import com.flowspeed.link.android.storage.BrowserBookmarksStorage
import com.flowspeed.link.android.storage.HomePageStorage
import com.flowspeed.link.android.storage.OnBoardingData
import com.flowspeed.link.android.util.FlowAppManager
import com.flowspeed.link.android.util.FlowServiceNotificationManager
import com.flowspeed.link.android.util.AndroidDefinedPaths
import com.flowspeed.link.android.util.AndroidDownloadItemOpener
import com.flowspeed.link.android.util.AppInfo
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.theme.ISystemThemeDetector
import com.flowspeed.lib.downloader.DownloadManagerMinimalControl
import com.flowspeed.lib.downloader.DownloadSettings
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.OkHttpHttpDownloaderClient
import com.flowspeed.lib.downloader.db.*
import com.flowspeed.lib.downloader.monitor.DownloadMonitor
import com.flowspeed.lib.downloader.utils.IDiskStat
import com.flowspeed.link.resources.FlowLanguageResources
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.downloaderinui.hls.HLSDownloaderInUi
import com.flowspeed.link.shared.downloaderinui.http.HttpDownloaderInUi
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.ExtraQueueSettingsStorage
import com.flowspeed.link.shared.storage.IExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.IExtraQueueSettingsStorage
import com.flowspeed.link.shared.storage.ILastSavedLocationsStorage
import com.flowspeed.link.shared.storage.PerHostSettingsDatastoreStorage
import com.flowspeed.link.shared.storage.ProxyDatastoreStorage
import com.flowspeed.link.shared.storage.impl.LastSavedLocationStorage
import com.flowspeed.link.shared.ui.theme.ThemeSettingsStorage
import com.flowspeed.link.shared.ui.widget.NotificationManager
import com.flowspeed.link.shared.updater.UpdateDownloaderViaDownloadSystem
import com.flowspeed.link.shared.util.AndroidDiskStat
import com.flowspeed.link.shared.util.AndroidSystemThemeDetector
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.UserAgentProviderFromSettings
import com.flowspeed.link.shared.util.*
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
import com.flowspeed.link.shared.util.ondownloadcompletion.NoOpOnDownloadCompletionActionProvider
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionRunner
import com.flowspeed.link.shared.util.onqueuecompletion.NoopOnQueueCompletionActionProvider
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueEventActionRunner
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.flowspeed.link.shared.util.perhostsettings.IPerHostSettingsStorage
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsItem
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.flowspeed.link.shared.util.ui.IMyIcons
import com.flowspeed.link.shared.util.proxy.IProxyStorage
import com.flowspeed.link.shared.util.proxy.ProxyData
import com.flowspeed.link.shared.util.proxy.ProxyManager
import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.connection.UserAgentProvider
import com.flowspeed.lib.downloader.connection.proxy.AutoConfigurableProxyProvider
import com.flowspeed.lib.downloader.connection.proxy.NoopSystemProxySelectorProvider
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
import com.flowspeed.lib.util.startup.AbstractStartupManager
import com.flowspeed.lib.util.startup.Startup
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import okhttp3.Protocol
import okhttp3.internal.tls.OkHostnameVerifier

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
        AndroidDiskStat()
    }
    single<ISystemThemeDetector> {
        AndroidSystemThemeDetector(get())
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
    single<SystemProxySelectorProvider> {
        NoopSystemProxySelectorProvider()
    }
    single<AutoConfigurableProxyProvider> {
        AutoConfigurableProxyProvider.NoOp()
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
                get<BaseAppSettingsStorage>().defaultDownloadFolder.value
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
            AndroidExtraDownloadItemSettings
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
            AndroidExtraQueueSettings
        )
    }.apply {
        bind<IExtraQueueSettingsStorage<*>>()
    }
    single<OnDownloadCompletionActionProvider> {
        NoOpOnDownloadCompletionActionProvider()
    }
    single<OnQueueCompletionActionProvider> {
        NoopOnQueueCompletionActionProvider()
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
    single {
        PermissionManager(
            FlowPermissions.importantPermissions,
            get(),
        )
    }
}
val coroutineModule = module {
    single {
        CoroutineScope(SupervisorJob())
    }
}
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
            }
        }
    }
}
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
        AndroidDirectLinkUpdateApplier(
            updateDownloader = UpdateDownloaderViaDownloadSystem(
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
                client = get<OkHttpClient>().newBuilder().build()
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
val startUpModule = module {
    single {
        Startup.getStartUpManager(get(), StartOnBootBroadcastReceiver::class.java)
    }.apply {
        bind<AbstractStartupManager>()
    }
}

fun getAppModule(context: FlowApp) = module {
    includes(downloaderModule)
    includes(downloadSystemModule)
    includes(coroutineModule)
    includes(jsonModule)
    includes(updaterModule)
    includes(startUpModule)
//    single {
//        NetworkChecker(get())
//    }
    single {
        AppInfo.definedPaths
    }.apply {
        bind<DefinedPaths>()
        bind<AndroidDefinedPaths>()
    }
    single {
        AppRepository(
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
//    single {
//        FontManager(get())
//    }
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
        RemovedDownloadsFromDiskTracker(
            get(), get(), get(),
        )
    }
    single {
        val definedPaths = get<DefinedPaths>()
        PreviousVersion(
            systemPath = definedPaths.systemDir.toFile(),
            currentVersion = AppVersion.get(),
        )
    }
    single {
        AppVersionTracker(
            previousVersion = {
                // it MUST be booted first
                get<PreviousVersion>().get()
            },
            currentVersion = AppVersion.get(),
        )
    }

    single {
        val appSettingsStorage: BaseAppSettingsStorage = get()
        AppSSLFactoryProvider(
            ignoreSSLCertificates = appSettingsStorage.ignoreSSLCertificates
        )
    }
    single {
        val appSettingsStorage: BaseAppSettingsStorage = get()
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
            .connectionPool(okhttp3.ConnectionPool(
                maxIdleConnections = 5,
                keepAliveDuration = 30,
                timeUnit = java.util.concurrent.TimeUnit.SECONDS
            ))
            .sslSocketFactory(
                appSSLFactoryProvider.createSSLSocketFactory(),
                appSSLFactoryProvider.trustManager,
            )
            .hostnameVerifier(appHostNameVerifier)
            .build()
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
    single {
        PerHostSettingsManager(get())
    }
    single { context }.apply {
        bind<FlowApp>()
        bind<Application>()
        bind<Context>()
    }
    single {
        FlowAppManager(get(), get(), get(), get(), get(), get(), get())
    }
    single {
        FlowServiceNotificationManager(get(), get(), get(), get(), get())
    }
    single {
        AndroidDownloadItemOpener(get())
    }.apply {
        bind<DownloadItemOpener>()
    }
    single { NotificationManager() }
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


object Di : KoinComponent {
    fun boot(applicationContext: FlowApp) {
        startKoin {
            modules(getAppModule(applicationContext))
        }
    }
}
