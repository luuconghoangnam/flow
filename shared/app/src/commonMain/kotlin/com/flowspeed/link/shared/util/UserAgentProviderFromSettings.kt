package com.flowspeed.link.shared.util

import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.lib.downloader.connection.UserAgentProvider

class UserAgentProviderFromSettings(
    private val appSettingsStorage: BaseAppSettingsStorage
) : UserAgentProvider {
    override fun getUserAgent(): String? {
        return appSettingsStorage.userAgent.value.takeIf { it.isNotBlank() }
    }
}
