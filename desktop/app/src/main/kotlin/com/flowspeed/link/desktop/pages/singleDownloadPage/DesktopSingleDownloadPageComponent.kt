package com.flowspeed.link.desktop.pages.singledownloadpage

import arrow.optics.copy
import com.flowspeed.link.desktop.storage.DesktopExtraDownloadItemSettings
import com.flowspeed.link.desktop.storage.PageStatesStorage
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.singledownloadpage.BaseSingleDownloadComponent
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ExtraDownloadSettingsStorage
import com.flowspeed.link.shared.ui.configurable.item.BooleanConfigurable
import com.flowspeed.link.shared.util.*
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.desktop.poweraction.PowerActionConfig
import com.flowspeed.lib.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import org.koin.core.component.get
import kotlin.getValue

class DesktopSingleDownloadComponent(
    ctx: ComponentContext,
    downloadItemOpener: DownloadItemOpener,
    onDismiss: () -> Unit,
    downloadId: Long,
    extraDownloadSettingsStorage: ExtraDownloadSettingsStorage<DesktopExtraDownloadItemSettings>,
    downloadSystem: DownloadSystem,
    appSettings: BaseAppSettingsStorage,
    appRepository: BaseAppRepository,
    applicationScope: CoroutineScope,
    fileIconProvider: FileIconProvider,
) : BaseSingleDownloadComponent<DesktopExtraDownloadItemSettings>(
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
    private val singleDownloadPageStateToPersist by lazy {
        get<PageStatesStorage>().singleDownloadPageState
    }
    override val defaultShowPartInfo: Boolean = singleDownloadPageStateToPersist.value.showPartInfo

    override fun setShowPartInfo(value: Boolean) {
        super.setShowPartInfo(value)
        singleDownloadPageStateToPersist.update {
            it.copy {
                SingleDownloadPageStateToPersist.showPartInfo.set(value)
            }
        }
    }

    sealed interface Effects : BaseSingleDownloadComponent.Effects.Platform {
        data object BringToFront : Effects
    }

    fun bringToFront() {
        sendEffect(Effects.BringToFront)
    }

    val onCompletion by lazy {
        listOf(
            BooleanConfigurable(
                title = Res.string.download_item_settings_shutdown_on_completion.asStringSource(),
                description = Res.string.download_item_settings_shutdown_on_completion_description.asStringSource(),
                backedBy = extraDownloadItemSettingsFlow.mapTwoWayStateFlow(
                    map = {
                        it.powerActionTypeOnFinish != null
                    },
                    unMap = {
                        copy(
                            powerActionTypeOnFinish = when (it) {
                                true -> PowerActionConfig.Type.Shutdown
                                false -> null
                            },
                        )
                    }
                ),
                describe = {
                    when (it) {
                        true -> Res.string.enabled
                        false -> Res.string.disabled
                    }.asStringSource()
                },
            ),
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


