package com.flowspeed.lib.downloader.connection

interface UserAgentProvider {
    fun getUserAgent(): String?
}
