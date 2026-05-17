package com.flowspeed.link.shared.pagemanager

import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.link.shared.pages.adddownload.ImportOptions

interface AddDownloadDialogManager {
    fun closeAddDownloadDialog()
    fun openAddDownloadDialog(
        links: List<AddDownloadCredentialsInUiProps>,
        importOptions: ImportOptions = ImportOptions(),
    )
}
