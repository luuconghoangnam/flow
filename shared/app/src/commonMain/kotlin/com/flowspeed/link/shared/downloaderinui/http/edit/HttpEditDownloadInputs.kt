package com.flowspeed.link.shared.downloaderinui.http.edit

import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.downloaderinui.DownloadSize
import com.flowspeed.link.shared.downloaderinui.LinkCheckerFactory
import com.flowspeed.link.shared.downloaderinui.edit.DownloadConflictDetector
import com.flowspeed.link.shared.downloaderinui.edit.EditDownloadCheckerFactory
import com.flowspeed.link.shared.downloaderinui.edit.EditDownloadInputs
import com.flowspeed.link.shared.downloaderinui.http.HttpCredentialsToItemMapper
import com.flowspeed.link.shared.downloaderinui.http.add.HttpLinkChecker
import com.flowspeed.link.shared.ui.configurable.item.FileChecksumConfigurable
import com.flowspeed.link.shared.ui.configurable.item.IntConfigurable
import com.flowspeed.link.shared.ui.configurable.item.SpeedLimitConfigurable
import com.flowspeed.link.shared.ui.configurable.item.StringConfigurable
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.ThreadCountLimitation
import com.flowspeed.link.shared.util.FileChecksum
import com.flowspeed.link.shared.util.convertPositiveSpeedToHumanReadable
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadItem
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.asStringSourceWithARgs
import com.flowspeed.lib.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HttpEditDownloadInputs(
    currentDownloadItem: MutableStateFlow<HttpDownloadItem>,
    editedDownloadItem: MutableStateFlow<HttpDownloadItem>,
    val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
    mapper: HttpCredentialsToItemMapper,
    conflictDetector: DownloadConflictDetector,
    scope: CoroutineScope,
    linkCheckerFactory: LinkCheckerFactory<HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes, HttpLinkChecker>,
    editDownloadCheckerFactory: EditDownloadCheckerFactory<HttpDownloadItem, HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes, HttpLinkChecker>
) : EditDownloadInputs<HttpDownloadItem, HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes, HttpLinkChecker, HttpCredentialsToItemMapper>(
    currentDownloadItem = currentDownloadItem,
    editedDownloadItem = editedDownloadItem,
    mapper = mapper,
    scope = scope,
    conflictDetector = conflictDetector,
    linkCheckerFactory = linkCheckerFactory,
    editDownloadCheckerFactory = editDownloadCheckerFactory,
) {

    override val configurableList = listOf(
        SpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = {
                    it.speedLimit
                },
                unMap = {
                    copy(speedLimit = it)
                }
            ),
            describe = {
                if (it == 0L) Res.string.unlimited.asStringSource()
                else convertPositiveSpeedToHumanReadable(it, sizeAndSpeedUnitProvider.speedUnit.value).asStringSource()
            }
        ),
        FileChecksumConfigurable(
            Res.string.download_item_settings_file_checksum.asStringSource(),
            Res.string.download_item_settings_file_checksum_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = {
                    it.fileChecksum?.let {
                        runCatching {
                            FileChecksum.Companion.fromString(it)
                        }.onFailure {
                            println(it.printStackTrace())
                        }.getOrNull()
                    }
                },
                unMap = {
                    copy(fileChecksum = it?.toString())
                }
            ),
            describe = { "".asStringSource() }
        ),
        IntConfigurable(
            Res.string.settings_download_thread_count.asStringSource(),
            Res.string.settings_download_thread_count_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = {
                    it.preferredConnectionCount ?: 0
                },
                unMap = {
                    copy(
                        preferredConnectionCount = it.takeIf { it >= 1 }
                    )
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
            backedBy = credentials.mapTwoWayStateFlow(
                map = {
                    it.username.orEmpty()
                },
                unMap = {
                    copy(username = it.takeIf { it.isNotEmpty() })
                }
            ),
            describe = {
                "".asStringSource()
            }
        ),
        StringConfigurable(
            Res.string.password.asStringSource(),
            Res.string.download_item_settings_password_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = {
                    it.password.orEmpty()
                },
                unMap = {
                    copy(password = it.takeIf { it.isNotEmpty() })
                }
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
        ),
    )
    val length = linkChecker.downloadSize
    override val downloadJobConfig: MutableStateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    private fun HttpDownloadItem.applyOurChanges(edited: HttpDownloadItem) {
        // we don't change some of these properties, so I commented them

        link = edited.link
        headers = edited.headers
        username = edited.username
        password = edited.password
        downloadPage = edited.downloadPage
        userAgent = edited.userAgent

//        id = edited.id
        folder = edited.folder
        name = edited.name

        contentLength = edited.contentLength
        serverETag = edited.serverETag

//        dateAdded = edited.dateAdded
//        startTime = edited.startTime
//        completeTime = edited.completeTime
//        status = edited.status
        preferredConnectionCount = edited.preferredConnectionCount
        speedLimit = edited.speedLimit

        fileChecksum = edited.fileChecksum
    }

    override fun applyEditedItemTo(item: HttpDownloadItem) {
        val edited = editedDownloadItem.value
        item.applyOurChanges(edited)
    }

    init {
        length.onEach {
            scheduleRefresh(alsoRecheckLink = false)
        }.launchIn(scope)
    }

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource {
        return downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
    }
}
