package com.flowspeed.link.desktop.di

import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.DownloadManagerMinimalControl
import com.flowspeed.lib.downloader.DownloadSettings
import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.OkHttpHttpDownloaderClient
import com.flowspeed.lib.downloader.connection.UserAgentProvider
import com.flowspeed.lib.downloader.connection.proxy.AutoConfigurableProxyProvider
import com.flowspeed.lib.downloader.connection.proxy.ProxyStrategyProvider
import com.flowspeed.lib.downloader.connection.proxy.SystemProxySelectorProvider
import com.flowspeed.lib.downloader.db.*
import com.flowspeed.lib.downloader.downloaditem.DownloadJob
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloader
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloader
import com.flowspeed.lib.downloader.monitor.DownloadItemStateFactory
import com.flowspeed.lib.downloader.monitor.DownloadMonitor
import com.flowspeed.lib.downloader.monitor.IDownloadMonitor
import com.flowspeed.lib.downloader.queue.ManualDownloadQueue
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.downloader.utils.EmptyFileCreator
import com.flowspeed.lib.downloader.utils.IDiskStat
import com.flowspeed.link.desktop.utils.proxy.AutoConfigurableProxyProviderForDesktop
import com.flowspeed.link.desktop.utils.proxy.DesktopSystemProxySelectorProvider
import com.flowspeed.link.desktop.utils.proxy.ProxyCachingConfig
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.downloaderinui.hls.HLSDownloaderInUi
import com.flowspeed.link.shared.downloaderinui.http.HttpDownloaderInUi
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.DesktopDiskStat
import com.flowspeed.link.shared.util.DesktopSystemThemeDetector
import com.flowspeed.link.shared.util.DownloadFoldersRegistry
import com.flowspeed.link.shared.util.UserAgentProviderFromSettings
import com.flowspeed.link.shared.util.proxy.ProxyManager
import com.flowspeed.link.shared.util.ui.theme.ISystemThemeDetector
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Download engine DI module.
 *
 * Provides: database layers, HTTP client, download manager,
 * queue manager, part storage, and download monitor.
 */
val downloaderModule = module {
    single<IDownloadQueueDatabase> {
        val definedPaths = get<DefinedPaths>()
        DownloadQueueFileStorageDatabase(
            queueFolder = get<DownloadFoldersRegistry>().registerAndGet(definedPaths.queuesDir),
            fileSaver = get(),
        )
    }
    single<IDownloadListDb> {
        val definedPaths = get<DefinedPaths>()
        DownloadListFileStorage(
            downloadListFolder = get<DownloadFoldersRegistry>().registerAndGet(definedPaths.downloadListDir),
            fileSaver = get(),
        )
    }
    single { TransactionalFileSaver(get()) }
    single<IDownloadPartListDb> {
        val definedPaths = get<DefinedPaths>()
        PartListFileStorage(
            get<DownloadFoldersRegistry>().registerAndGet(definedPaths.partsDir),
            get()
        )
    }
    single<IDiskStat> { DesktopDiskStat() }
    single<ISystemThemeDetector> { DesktopSystemThemeDetector() }
    single { QueueManager(get(), get()) }
    single { DownloadFoldersRegistry() }
    single { DownloadSettings(8) }
    single { ProxyManager(get()) }.bind<ProxyStrategyProvider>()
    single { ProxyCachingConfig.default() }
    single<AutoConfigurableProxyProvider> { AutoConfigurableProxyProviderForDesktop(get()) }
    single<SystemProxySelectorProvider> { DesktopSystemProxySelectorProvider(get()) }
    single<UserAgentProvider> { UserAgentProviderFromSettings(get()) }
    single<HttpDownloaderClient> { OkHttpHttpDownloaderClient(get(), get(), get(), get(), get()) }
    single {
        val downloadSettings: DownloadSettings = get()
        EmptyFileCreator(diskStat = get(), useSparseFile = { downloadSettings.useSparseFileAllocation })
    }
    single { HLSDownloader(inject()) }
    single { HLSDownloaderInUi(get(), get()) }
    single { HttpDownloader(inject()) }
    single { HttpDownloaderInUi(get(), get()) }
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
            get(), get(), get(), get(), get(),
            get<DownloadFoldersRegistry>().registerAndGet(definedPaths.downloadDataDir)
        )
    }.bind(DownloadManagerMinimalControl::class)
    single { ManualDownloadQueue(get(), get()) }
    single<IDownloadMonitor> {
        DownloadMonitor(
            downloadManager = get(),
            manualDownloadQueue = get(),
            downloadItemStateFactory = inject(),
        )
    }
}
