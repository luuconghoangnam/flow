package com.flowspeed.link.shared.pages.editdownload

import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.downloaderinui.edit.DownloadConflictDetector
import com.flowspeed.link.shared.downloaderinui.edit.EditDownloadInputs
import com.flowspeed.link.shared.util.BaseComponent
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.FileIconProvider
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

open class BaseEditDownloadComponent(
    ctx: ComponentContext,
    private val downloaderInUiRegistry: DownloaderInUiRegistry,
    val iconProvider: FileIconProvider,
    val downloadSystem: DownloadSystem,
    val onRequestClose: () -> Unit,
    val downloadId: Long,
    val acceptEdit: StateFlow<Boolean>,
    private val onEdited: ((IDownloadItem) -> Unit, DownloadJobExtraConfig?) -> Unit,
) : BaseComponent(ctx) {

    val editDownloadUiChecker =
        MutableStateFlow(null as EditDownloadInputs<IDownloadItem, IDownloadCredentials, *, *, *, *>?)

    init {
        scope.launch {
            load(downloadId)
        }
    }

    private var pendingCredential: IDownloadCredentials? = null
    private val _credentialsImportedFromExternal = MutableStateFlow(false)
    val credentialsImportedFromExternal = _credentialsImportedFromExternal.asStateFlow()
    fun importCredential(credentials: IDownloadCredentials) {
        editDownloadUiChecker.value?.let {
            it.importCredentials(credentials)
        } ?: run {
            pendingCredential = credentials
        }
        _credentialsImportedFromExternal.value = true
    }

    private suspend fun load(id: Long) {
        val downloadItem = downloadSystem.getDownloadItemById(id = id)
        if (downloadItem == null) {
            onRequestClose()
            println("item with id $id not found")
            return
        }
        val downloader = downloaderInUiRegistry.getDownloaderOf(downloadItem)
        if (downloader == null) {
            onRequestClose()
            println("downloader for id $id not found")
            return
        }
        val httpEditDownloadInputs = downloader.createEditDownloadInputs(
            currentDownloadItem = MutableStateFlow(downloadItem),
            editedDownloadItem = MutableStateFlow(downloadItem),
            conflictDetector = DownloadConflictDetector(downloadSystem),
            scope = scope,
        )
        editDownloadUiChecker.value = httpEditDownloadInputs
        pendingCredential?.let { credentials ->
            httpEditDownloadInputs.importCredentials(credentials)
            pendingCredential = null
        }
    }


    fun onRequestEdit() {
        if (!acceptEdit.value) {
            return
        }
        editDownloadUiChecker.value?.let { editDownloadUiChecker ->
            onEdited(editDownloadUiChecker::applyEditedItemTo, editDownloadUiChecker.downloadJobConfig.value)
        }
    }
}
