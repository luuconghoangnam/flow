package com.flowspeed.link.desktop.actions.onevennts

import com.flowspeed.link.desktop.PowerActionManager
import com.flowspeed.lib.util.desktop.poweraction.PowerActionConfig
import com.flowspeed.link.desktop.pages.poweractionalert.PowerActionComponent
import com.flowspeed.link.desktop.storage.DesktopExtraDownloadItemSettings
import com.flowspeed.link.shared.storage.ExtraDownloadSettingsStorage
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionAction
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class DesktopOnDownloadCompletionActionProvider(
    private val extraDownloadSettingsStorage: ExtraDownloadSettingsStorage<DesktopExtraDownloadItemSettings>,
) : OnDownloadCompletionActionProvider, KoinComponent {
    // TODO: BUG
    // at the moment if I move this to constructor the DI halts
    // probably due to Circular Dependency Exception
    // I need to redesign the dependency graph to prevent these sorts of issues!
    private val powerActionManager: PowerActionManager by inject()

    override suspend fun getOnDownloadCompletionAction(downloadItem: IDownloadItem): List<OnDownloadCompletionAction> {
        val downloadId = downloadItem.id
        val extraDownloadItemSettings = extraDownloadSettingsStorage.getExtraDownloadItemSettings(downloadId)
        return buildList {
            extraDownloadItemSettings.getPowerActionConfigOnFinish()?.let {
                add(PowerActionOnDownloadFinish(powerActionManager, it))
            }
            add(
                CleanExtraSettingsOnDownloadFinish(extraDownloadSettingsStorage)
            )
        }
    }
}

class PowerActionOnDownloadFinish(
    val powerActionManager: PowerActionManager,
    val powerActionConfig: PowerActionConfig,
) : OnDownloadCompletionAction {
    override suspend fun onDownloadCompleted(downloadItem: IDownloadItem) {
        powerActionManager.initiatePowerAction(
            powerActionConfig,
            PowerActionComponent.PowerActionReason.DownloadFinished,
        )
    }
}

