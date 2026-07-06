package com.flowspeed.link.desktop.integration

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.link.shared.pages.adddownload.ImportOptions
import com.flowspeed.link.shared.pages.adddownload.SilentImportOptions
import com.flowspeed.link.desktop.ui.Ui
import com.flowspeed.link.desktop.repository.AppRepository
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.integration.IntegrationHandler
import com.flowspeed.link.integration.HttpDownloadCredentialsFromIntegration
import com.flowspeed.link.integration.NewDownloadTask
import com.flowspeed.link.integration.ApiQueueModel
import com.flowspeed.link.integration.AddDownloadOptionsFromIntegration
import com.flowspeed.link.integration.HLSDownloadCredentialsFromIntegration
import com.flowspeed.link.integration.IDownloadCredentialsFromIntegration
import com.flowspeed.link.shared.downloaderinui.BasicDownloadItem
import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadCredentials
import com.flowspeed.lib.downloader.NewDownloadItemProps
import com.flowspeed.lib.downloader.downloaditem.EmptyContext
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.queue.QueueManager
import com.flowspeed.lib.downloader.utils.OnDuplicateStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IntegrationHandlerImp : IntegrationHandler, KoinComponent {
    val appComponent by inject<AppComponent>()
    val downloadSystem by inject<DownloadSystem>()
    val queueManager by inject<QueueManager>()
    val appSettings by inject<AppRepository>()
    private val downloaderInUiRegistry by inject<DownloaderInUiRegistry>()

    override suspend fun addDownload(
        list: List<IDownloadCredentialsFromIntegration>,
        options: AddDownloadOptionsFromIntegration,
    ) {
        if (Ui.isComposeActive) {
            // UI is open → show Add Download dialog (existing flow)
            appComponent.externalCredentialComingIntoApp(
                list.map { convertToDownloadSystemCredentials(it) },
                options = ImportOptions(
                    silentImport = if (options.silentAdd) {
                        SilentImportOptions(silentDownload = options.silentStart)
                    } else null
                )
            )
        } else {
            // UI is hidden (tray mode) → download immediately to default folder
            silentDownloadFromTray(list)
        }
    }

    /**
     * Downloads files immediately without showing any UI.
     * Used when app is in tray mode (Compose inactive).
     * Sends native OS notification via tray icon.
     */
    private suspend fun silentDownloadFromTray(list: List<IDownloadCredentialsFromIntegration>) {
        val startedNames = mutableListOf<String>()
        for (item in list) {
            val credentials = convertToDownloadSystemCredentials(item)
            val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(credentials.credentials)
                ?: continue // skip unsupported link types

            val filename = credentials.extraConfig.suggestedName
                ?: item.link.substringAfterLast("/").substringBefore("?").takeIf { it.isNotEmpty() }
                ?: "download"

            val downloadItem = downloaderInUi.createBareDownloadItem(
                credentials.credentials,
                basicDownloadItem = BasicDownloadItem(
                    folder = appSettings.saveLocation.value,
                    name = filename,
                ),
            )
            val id = downloadSystem.addDownload(
                newDownload = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    onDuplicateStrategy = OnDuplicateStrategy.AddNumbered,
                    extraConfig = null,
                    context = EmptyContext,
                ),
                queueId = null,
                categoryId = null,
            )
            downloadSystem.userManualResume(id)
            startedNames.add(filename)
        }
        // Send a single tray notification summarizing what started
        if (startedNames.isNotEmpty()) {
            val message = if (startedNames.size == 1) {
                startedNames.first()
            } else {
                "${startedNames.size} downloads started:\n${startedNames.joinToString("\n") { "• $it" }}"
            }
            Ui.notifyFromTray("Download Started", message)
        }
    }

    override fun listQueues(): List<ApiQueueModel> {
        return queueManager.getAll().map { downloadQueue ->
            val queueModel = downloadQueue.getQueueModel()
            ApiQueueModel(id = queueModel.id, name = queueModel.name)
        }
    }
    override suspend fun addDownloadTask(task: NewDownloadTask) {
        val addDownloaderInUiProps = convertToDownloadSystemCredentials(task.downloadSource)
        val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(
            addDownloaderInUiProps.credentials
        ) ?: error("Downloader for ${addDownloaderInUiProps.credentials::class.qualifiedName} not found")
        val downloadItem = downloaderInUi.createBareDownloadItem(
            addDownloaderInUiProps.credentials,
            basicDownloadItem = BasicDownloadItem(
                folder = task.folder ?: appSettings.saveLocation.value,
                name = task.name ?: addDownloaderInUiProps.extraConfig.suggestedName
                ?: task.downloadSource.link.substringAfterLast("/"),
            ),
        )
        val id =
            downloadSystem.addDownload(
                newDownload = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    onDuplicateStrategy = OnDuplicateStrategy.default(),
                    extraConfig = null,
                    context = EmptyContext,
                ),
                queueId = task.queueId,
                categoryId = null
            )
        if (task.queueId != null) {
            val queue = queueManager.getQueue(task.queueId!!)
            queue.start()
        } else {
            downloadSystem.userManualResume(id)
        }
    }

    companion object {
        private fun convertToDownloadSystemCredentials(it: IDownloadCredentialsFromIntegration): AddDownloadCredentialsInUiProps {
            val credentials = when (it) {
                is HttpDownloadCredentialsFromIntegration -> {
                    HttpDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                    )
                }

                is HLSDownloadCredentialsFromIntegration -> {
                    HLSDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                    )
                }
            }
            return AddDownloadCredentialsInUiProps(
                credentials = credentials,
                extraConfig = AddDownloadCredentialsInUiProps.Configs(
                    suggestedName = it.suggestedName,
                )
            )
        }
    }
}
