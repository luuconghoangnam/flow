package com.flowspeed.link.android.pages.singledownload

import com.flowspeed.link.android.storage.AndroidExtraDownloadItemSettings
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.singledownloadpage.BaseSingleDownloadComponent
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ExtraDownloadSettingsStorage
import com.flowspeed.link.shared.ui.configurable.item.BooleanConfigurable
import com.flowspeed.link.shared.util.*
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlin.getValue

class AndroidSingleDownloadComponent(
    ctx: ComponentContext,
    downloadItemOpener: DownloadItemOpener,
    onDismiss: () -> Unit,
    downloadId: Long,
    extraDownloadSettingsStorage: ExtraDownloadSettingsStorage<AndroidExtraDownloadItemSettings>,
    downloadSystem: DownloadSystem,
    appSettings: BaseAppSettingsStorage,
    appRepository: BaseAppRepository,
    applicationScope: CoroutineScope,
    fileIconProvider: FileIconProvider,
    val comesFromExternalApplication: Boolean,
) : BaseSingleDownloadComponent<AndroidExtraDownloadItemSettings>(
    ctx = ctx,
    downloadItemOpener = downloadItemOpener,
    onDismiss = onDismiss,
    downloadId = downloadId,
    extraDownloadSettingsStorage = extraDownloadSettingsStorage,
    downloadSystem = downloadSystem,
    appSettings = appSettings,
    appRepository = appRepository,
    applicationScope = applicationScope,
    fileIconProvider = fileIconProvider,
) {
    override val defaultShowPartInfo: Boolean = false
//    private val singleDownloadPageStateToPersist by lazy {
//        get<PageStatesStorage>().singleDownloadPageState
//    }
//    override fun setShowPartInfo(value: Boolean) {
//        super.setShowPartInfo(value)
//        singleDownloadPageStateToPersist.update {
//            it.copy {
//                SingleDownloadPageStateToPersist.showPartInfo.set(value)
//            }
//        }
//    }

    sealed interface Effects : BaseSingleDownloadComponent.Effects.Platform {
    }

    val onCompletion by lazy {
        listOf(
            BooleanConfigurable(
                title = Res.string.download_item_settings_show_download_completion_dialog.asStringSource(),
                description = Res.string.download_item_settings_show_download_completion_dialog_description.asStringSource(),
                backedBy = itemShouldShowCompletionDialog.mapTwoWayStateFlow(
                    map = {
                        it ?: globalShowCompletionDialog.value
                    },
                    unMap = { it }
                ),
                describe = {
                    when (it) {
                        true -> Res.string.enabled
                        false -> Res.string.disabled
                    }.asStringSource()
                },
            ),
        )
    }

    data class Config(
        override val id: Long
    ) : BaseSingleDownloadComponent.Config
}


