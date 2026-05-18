package com.flowspeed.link.service

import com.flowspeed.lib.downloader.DownloadManager
import com.flowspeed.lib.downloader.DownloadSettings
import com.flowspeed.lib.downloader.DownloaderRegistry
import com.flowspeed.lib.downloader.db.*
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloader
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadItem
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloader
import com.flowspeed.lib.downloader.monitor.DownloadMonitor
import com.flowspeed.lib.downloader.queue.ManualDownloadQueue
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.downloader.utils.EmptyFileCreator
import com.flowspeed.link.shared.util.DesktopDiskStat
import com.flowspeed.link.shared.util.DownloadFoldersRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Manual dependency injection for the background service.
 *
 * No Koin, no reflection — all dependencies are wired explicitly.
 * This ensures GraalVM native-image compatibility.
 *
 * Dependencies are lazy-initialized to minimize startup time and memory.
 */
class ServiceDi(private val config: ServiceConfig) {

    // --- Core ---
    val scope = CoroutineScope(SupervisorJob())
    val dataDir: File get() = config.dataDir

    // --- Serialization ---
    val json: Json by lazy {
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
            }
        }
    }

    // --- Storage ---
    val foldersRegistry by lazy { DownloadFoldersRegistry() }
    val downloadSettings by lazy { DownloadSettings(8) }
    val fileSaver by lazy { TransactionalFileSaver(json) }

    val downloadListDb: IDownloadListDb by lazy {
        DownloadListFileStorage(
            downloadListFolder = foldersRegistry.registerAndGet(
                File(dataDir, "downloads").toOkioPath()
            ),
            fileSaver = fileSaver,
        )
    }

    val partListDb: IDownloadPartListDb by lazy {
        PartListFileStorage(
            foldersRegistry.registerAndGet(File(dataDir, "parts").toOkioPath()),
            fileSaver
        )
    }

    val queueDatabase: IDownloadQueueDatabase by lazy {
        DownloadQueueFileStorageDatabase(
            queueFolder = foldersRegistry.registerAndGet(File(dataDir, "queues").toOkioPath()),
            fileSaver = fileSaver,
        )
    }

    // --- Download Engine ---
    val emptyFileCreator by lazy {
        EmptyFileCreator(
            diskStat = DesktopDiskStat(),
            useSparseFile = { downloadSettings.useSparseFileAllocation }
        )
    }

    val httpDownloaderClient: com.flowspeed.lib.downloader.connection.HttpDownloaderClient by lazy {
        com.flowspeed.lib.downloader.connection.JavaHttpDownloaderClient(
            customUserAgentProvider = object : com.flowspeed.lib.downloader.connection.UserAgentProvider {
                override fun getUserAgent(): String? = null
            },
            proxyStrategyProvider = object : com.flowspeed.lib.downloader.connection.proxy.ProxyStrategyProvider {
                override fun getProxyStrategyFor(url: String) = com.flowspeed.lib.downloader.connection.proxy.ProxyStrategy.Direct
            },
            systemProxySelectorProvider = com.flowspeed.lib.downloader.connection.proxy.NoopSystemProxySelectorProvider(),
            autoConfigurableProxyProvider = com.flowspeed.lib.downloader.connection.proxy.AutoConfigurableProxyProvider.NoOp(),
            ignoreSSL = { false },
        )
    }

    val httpDownloader by lazy { HttpDownloader(lazy { httpDownloaderClient }) }
    val hlsDownloader by lazy { HLSDownloader(lazy { httpDownloaderClient }) }

    val downloaderRegistry by lazy {
        DownloaderRegistry().apply {
            add(httpDownloader)
            add(hlsDownloader)
        }
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            dlListDb = downloadListDb,
            partListDb = partListDb,
            settings = downloadSettings,
            emptyFileCreator = emptyFileCreator,
            downloaderRegistry = downloaderRegistry,
            downloadDataFolder = foldersRegistry.registerAndGet(
                File(dataDir, "download_data").toOkioPath()
            )
        )
    }

    // --- Queue ---
    val queueManager by lazy { QueueManager(queueDatabase, downloadManager) }
    val manualDownloadQueue by lazy { ManualDownloadQueue(downloadManager, scope) }

    // --- Monitor ---
    // Note: DownloaderInUiRegistry requires SizeAndSpeedUnitProvider which is UI-specific.
    // For the headless service, we use a minimal stub that provides default units.
    val downloadMonitor by lazy {
        DownloadMonitor(
            downloadManager = downloadManager,
            manualDownloadQueue = manualDownloadQueue,
            downloadItemStateFactory = lazy {
                val stubUnitProvider = object : com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider {
                    override val sizeUnit: kotlinx.coroutines.flow.StateFlow<com.flowspeed.lib.util.datasize.ConvertSizeConfig> =
                        kotlinx.coroutines.flow.MutableStateFlow(com.flowspeed.lib.util.datasize.CommonSizeConvertConfigs.BinaryBytes)
                    override val speedUnit: kotlinx.coroutines.flow.StateFlow<com.flowspeed.lib.util.datasize.ConvertSizeConfig> =
                        kotlinx.coroutines.flow.MutableStateFlow(com.flowspeed.lib.util.datasize.CommonSizeConvertConfigs.BinaryBytes)
                }
                com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry().apply {
                    add(com.flowspeed.link.shared.downloaderinui.http.HttpDownloaderInUi(httpDownloader, stubUnitProvider))
                    add(com.flowspeed.link.shared.downloaderinui.hls.HLSDownloaderInUi(hlsDownloader, stubUnitProvider))
                }
            },
        )
    }
}
