package com.flowspeed.lib.downloader.downloaditem.http

import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials

interface IHttpBasedDownloadCredentials : IDownloadCredentials {
    val headers: Map<String, String>?
    val username: String?
    val password: String?
    val userAgent: String?
}
