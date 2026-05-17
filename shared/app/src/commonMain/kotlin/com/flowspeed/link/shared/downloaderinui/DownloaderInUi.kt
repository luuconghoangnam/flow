package com.flowspeed.link.shared.downloaderinui

import com.flowspeed.link.shared.downloaderinui.add.NewDownloadInputs
import com.flowspeed.link.shared.downloaderinui.add.NewDownloadInputsFactory
import com.flowspeed.link.shared.downloaderinui.edit.EditDownloadCheckerFactory
import com.flowspeed.link.shared.downloaderinui.edit.EditDownloadInputs
import com.flowspeed.link.shared.downloaderinui.edit.EditDownloadInputsFactory
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.lib.downloader.Downloader
import com.flowspeed.lib.downloader.connection.IResponseInfo
import com.flowspeed.lib.downloader.downloaditem.DownloadJob
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState
import com.flowspeed.lib.downloader.monitor.DownloadItemStateFactory
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemFactoryInputs
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.lib.util.compose.StringSource
import kotlinx.coroutines.CoroutineScope

/**
 * This is a class that represent a downloader implementation details tight to the Application not just the downloader logic
 * including ui, component factories and every thing that app need work with
 */
abstract class DownloaderInUi<
        TCredentials : IDownloadCredentials,
        TResponseInfo : IResponseInfo,
        TDownloadSize : DownloadSize,
        TLinkChecker : LinkChecker<TCredentials, TResponseInfo, TDownloadSize>,
        TDownloadItem : IDownloadItem,
        TNewDownloadInputs : NewDownloadInputs<TDownloadItem, TCredentials, TResponseInfo, TDownloadSize, TLinkChecker>,
        TEditDownloadInputs : EditDownloadInputs<TDownloadItem, TCredentials, TResponseInfo, TDownloadSize, TLinkChecker, TCredentialAndItemMapper>,
        TCredentialAndItemMapper : CredentialAndItemMapper<TCredentials, TDownloadItem>,
        TDownloadJob : DownloadJob,
        TDownloader : Downloader<TDownloadItem, TDownloadJob, TCredentials>
        >(
    val downloader: TDownloader
) :
    LinkCheckerFactory<TCredentials, TResponseInfo, TDownloadSize, TLinkChecker>,
    EditDownloadCheckerFactory<TDownloadItem, TCredentials, TResponseInfo, TDownloadSize, TLinkChecker>,
    NewDownloadInputsFactory<TDownloadItem, TCredentials, TResponseInfo, TDownloadSize, TLinkChecker, TNewDownloadInputs>,
    EditDownloadInputsFactory<TDownloadItem, TCredentials, TResponseInfo, TDownloadSize, TLinkChecker, TCredentialAndItemMapper, TEditDownloadInputs>,
    DownloadItemStateFactory<TDownloadItem, TDownloadJob> {
    abstract fun newDownloadUiChecker(
        initialCredentials: TCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope,
    ): DownloadUiChecker<TCredentials, TResponseInfo, TDownloadSize, TLinkChecker>


    abstract fun acceptDownloadCredentials(item: IDownloadCredentials): Boolean
    abstract fun supportsThisLink(link: String): Boolean
    abstract fun createMinimumCredentials(link: String): TCredentials
    abstract fun createBareDownloadItem(
        credentials: TCredentials,
        basicDownloadItem: BasicDownloadItem
    ): TDownloadItem

    abstract override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<TDownloadJob>,
    ): ProcessingDownloadItemState

    override fun createCompletedDownloadItemState(
        downloadItem: TDownloadItem,
    ): CompletedDownloadItemState {
        return CompletedDownloadItemState.fromDownloadItem(downloadItem)
    }

    abstract val name: StringSource
}
