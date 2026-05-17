package com.flowspeed.link.shared.downloaderinui.hls.add

import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.downloaderinui.add.NewDownloadInputs
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadCredentials
import com.flowspeed.link.shared.downloaderinui.hls.HLSLinkChecker
import com.flowspeed.lib.downloader.downloaditem.hls.HLSResponseInfo
import com.flowspeed.link.shared.downloaderinui.http.applyToHttpDownload
import com.flowspeed.link.shared.ui.configurable.item.FileChecksumConfigurable
import com.flowspeed.link.shared.ui.configurable.item.IntConfigurable
import com.flowspeed.link.shared.ui.configurable.item.SpeedLimitConfigurable
import com.flowspeed.link.shared.ui.configurable.item.StringConfigurable
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.ThreadCountLimitation
import com.flowspeed.link.shared.util.FileChecksum
import com.flowspeed.link.shared.util.convertPositiveSpeedToHumanReadable
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsItem
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.DownloadStatus
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadItem
import com.flowspeed.lib.downloader.downloaditem.hls.HLSDownloadJobExtraConfig
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.asStringSourceWithARgs
import com.flowspeed.lib.util.flow.combineStateFlows
import com.flowspeed.lib.util.flow.createMutableStateFlowFromStateFlow
import com.flowspeed.lib.util.flow.mapStateFlow
import com.flowspeed.lib.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HLSNewDownloadInputs(
    downloadUiChecker: HLSDownloadUIChecker,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
    private val scope: CoroutineScope,
) : NewDownloadInputs<
        HLSDownloadItem,
        HLSDownloadCredentials,
        HLSResponseInfo,
        DownloadSize.Duration,
        HLSLinkChecker,
        >(
    downloadUiChecker = downloadUiChecker
) {

    //extra settings
    private var threadCount = MutableStateFlow(null as Int?)
    private var speedLimit = MutableStateFlow(0L)
    private var fileChecksum = MutableStateFlow(null as FileChecksum?)
    override val downloadItem: StateFlow<HLSDownloadItem> = combineStateFlows(
        this.credentials,
        this.folder,
        this.name,
        this.downloadSize,
        this.speedLimit,
        this.threadCount,
        this.fileChecksum,
    ) { credentials,
        folder,
        name,
        duration,
        speedLimit,
        threadCount,
        fileChecksum
        ->
        HLSDownloadItem(
            id = -1,
            folder = folder,
            name = name,
            link = credentials.link,
            dateAdded = openedTime,
            startTime = null,
            completeTime = null,
            status = DownloadStatus.Added,
            preferredConnectionCount = threadCount,
            speedLimit = speedLimit,
            fileChecksum = fileChecksum?.toString(),
            duration = duration?.duration,
        ).withCredentials(credentials)
    }
    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = downloadUiChecker.responseInfo.mapStateFlow {
        it?.let {
            HLSDownloadJobExtraConfig(
                hlsManifest = it.hlsManifest
            )
        }
    }

    override fun applyHostSettingsToExtraConfig(extraConfig: PerHostSettingsItem) {
        extraConfig.applyToHttpDownload(
            setUsername = { setCredentials(credentials.value.copy(username = it)) },
            setPassword = { setCredentials(credentials.value.copy(password = it)) },
            setUserAgent = { setCredentials(credentials.value.copy(userAgent = it)) },
            setThreadCount = { threadCount.value = it },
            setSpeedLimit = { speedLimit.value = it }
        )
    }

    override val configurableList = listOf(
        SpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = speedLimit,
            describe = {
                if (it == 0L) Res.string.unlimited.asStringSource()
                else convertPositiveSpeedToHumanReadable(
                    it, sizeAndSpeedUnitProvider.speedUnit.value
                ).asStringSource()
            }
        ),
        FileChecksumConfigurable(
            Res.string.download_item_settings_file_checksum.asStringSource(),
            Res.string.download_item_settings_file_checksum_description.asStringSource(),
            backedBy = fileChecksum,
            describe = { "".asStringSource() }
        ),
        IntConfigurable(
            Res.string.settings_download_thread_count.asStringSource(),
            Res.string.settings_download_thread_count_description.asStringSource(),
            backedBy = threadCount.mapTwoWayStateFlow(
                map = {
                    it ?: 0
                },
                unMap = {
                    it.takeIf { it >= 1 }
                }
            ),
            range = 0..ThreadCountLimitation.MAX_ALLOWED_THREAD_COUNT,
            describe = {
                if (it == 0) Res.string.use_global_settings.asStringSource()
                else Res.string.download_item_settings_thread_count_describe
                    .asStringSourceWithARgs(
                        Res.string.download_item_settings_thread_count_describe_createArgs(
                            count = it.toString()
                        )
                    )
            }
        ),
        StringConfigurable(
            Res.string.username.asStringSource(),
            Res.string.download_item_settings_username_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = credentials.mapStateFlow {
                    it.username.orEmpty()
                },
                updater = {
                    setCredentials(credentials.value.copy(username = it.takeIf { it.isNotBlank() }))
                }, scope
            ),
            describe = {
                "".asStringSource()
            }
        ),
        StringConfigurable(
            Res.string.password.asStringSource(),
            Res.string.download_item_settings_password_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = credentials.mapStateFlow {
                    it.password.orEmpty()
                },
                updater = {
                    setCredentials(credentials.value.copy(password = it.takeIf { it.isNotBlank() }))
                }, scope
            ),
            describe = {
                "".asStringSource()
            }
        ),
        StringConfigurable(
            Res.string.download_item_settings_user_agent.asStringSource(),
            Res.string.download_item_settings_user_agent_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = {
                    it.userAgent.orEmpty()
                },
                unMap = {
                    copy(userAgent = it.takeIf { it.isNotEmpty() })
                }
            ),
            describe = {
                "".asStringSource()
            }
        ),
        StringConfigurable(
            Res.string.download_item_settings_download_page.asStringSource(),
            Res.string.download_item_settings_download_page_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = {
                    it.downloadPage.orEmpty()
                },
                unMap = {
                    copy(downloadPage = it.takeIf { it.isNotEmpty() })
                }
            ),
            describe = {
                "".asStringSource()
            }
        )
    )

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Duration): StringSource {
        return downloadSize.asStringSource()
    }
}
