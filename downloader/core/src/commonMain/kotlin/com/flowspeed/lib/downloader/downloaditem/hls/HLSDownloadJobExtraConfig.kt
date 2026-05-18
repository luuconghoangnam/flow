package com.flowspeed.lib.downloader.downloaditem.hls

import io.lindstrom.m3u8.model.MediaPlaylist
import com.flowspeed.lib.downloader.downloaditem.DownloadJobExtraConfig

data class HLSDownloadJobExtraConfig(
    val hlsManifest: MediaPlaylist? = null
) : DownloadJobExtraConfig
