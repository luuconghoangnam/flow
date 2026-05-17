package com.flowspeed.link.shared.downloaderinui.hls

import com.flowspeed.link.shared.downloaderinui.BasicDownloadItem
import com.flowspeed.link.shared.downloaderinui.DownloaderInUi
import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.downloaderinui.edit.DownloadConflictDetector
import com.flowspeed.link.shared.downloaderinui.hls.add.HLSDownloadUIChecker
import com.flowspeed.link.shared.downloaderinui.hls.add.HLSNewDownloadInputs
import com.flowspeed.link.shared.downloaderinui.hls.edit.HLSEditDownloadChecker
import com.flowspeed.link.shared.downloaderinui.hls.edit.HLSEditDownloadInputs
import com.flowspeed.link.shared.downloaderinui.http.edit.EditDownloadChecker
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadItem
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadJob
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloader
import com.flowspeed.lib.downloader.downloaditem.hls.HLSResponseInfo
import com.flowspeed.lib.downloader.downloaditem.hls.IHLSCredentials
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemFactoryInputs
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.lib.util.HttpUrlUtils
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class HLSDownloaderInUi(
    downloader: HLSDownloader,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : DownloaderInUi<
        HLSDownloadCredentials,
        HLSResponseInfo,
        DownloadSize.Duration,
        HLSLinkChecker,
        HLSDownloadItem,
        HLSNewDownloadInputs,
        HLSEditDownloadInputs,
        HlsItemToCredentialMapper,
        HLSDownloadJob,
        HLSDownloader
        >(downloader) {
    override fun newDownloadUiChecker(
        initialCredentials: HLSDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ): HLSDownloadUIChecker {
        return HLSDownloadUIChecker(
            initCredentials = initialCredentials,
            linkCheckerFactory = this,
            initialFolder = initialFolder,
            initialName = initialName,
            downloadSystem = downloadSystem,
            scope = scope,
        )
    }

    override fun acceptDownloadCredentials(item: IDownloadCredentials): Boolean {
        return item is IHLSCredentials
    }

    override fun supportsThisLink(link: String): Boolean {
        return HttpUrlUtils.isValidUrl(link)
    }

    override fun createMinimumCredentials(link: String): HLSDownloadCredentials {
        return HLSDownloadCredentials(link = link)
    }

    override fun createBareDownloadItem(
        credentials: HLSDownloadCredentials,
        basicDownloadItem: BasicDownloadItem
    ): HLSDownloadItem {
        return HLSDownloadItem.createWithCredentials(
            id = -1,
            credentials = credentials,
            folder = basicDownloadItem.folder,
            name = basicDownloadItem.name,
            contentLength = basicDownloadItem.contentLength,
            preferredConnectionCount = basicDownloadItem.preferredConnectionCount,
            speedLimit = basicDownloadItem.speedLimit,
            fileChecksum = basicDownloadItem.fileChecksum,
        )
    }

    override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<HLSDownloadJob>
    ): ProcessingDownloadItemState {
        return UiProcessingItemForHSLFactory.create(
            props,
        )
    }

    override val name: StringSource = "HLS".asStringSource()

    override fun createLinkChecker(initialCredentials: HLSDownloadCredentials): HLSLinkChecker {
        return HLSLinkChecker(
            credentials = initialCredentials,
            client = downloader.client
        )
    }

    override fun createEditDownloadChecker(
        currentDownloadItem: MutableStateFlow<HLSDownloadItem>,
        editedDownloadItem: MutableStateFlow<HLSDownloadItem>,
        linkChecker: HLSLinkChecker,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope
    ): EditDownloadChecker<HLSDownloadItem, HLSDownloadCredentials, HLSResponseInfo, DownloadSize.Duration, HLSLinkChecker> {
        return HLSEditDownloadChecker(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            linkChecker = linkChecker,
            conflictDetector = conflictDetector,
            scope = scope,
        )
    }

    override fun createNewDownloadInputs(
        initialCredentials: HLSDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ): HLSNewDownloadInputs {
        return HLSNewDownloadInputs(
            newDownloadUiChecker(
                initialCredentials = initialCredentials,
                initialFolder = initialFolder,
                initialName = initialName,
                downloadSystem = downloadSystem,
                scope = scope,
            ),
            sizeAndSpeedUnitProvider,
            scope,
        )
    }

    override fun createEditDownloadInputs(
        currentDownloadItem: MutableStateFlow<HLSDownloadItem>,
        editedDownloadItem: MutableStateFlow<HLSDownloadItem>,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope
    ): HLSEditDownloadInputs {
        return HLSEditDownloadInputs(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            mapper = HlsItemToCredentialMapper(),
            conflictDetector = conflictDetector,
            scope = scope,
            linkCheckerFactory = this,
            editDownloadCheckerFactory = this,
            sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
        )
    }
}

