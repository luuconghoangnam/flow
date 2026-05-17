package com.flowspeed.link.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowspeed.link.android.pages.onboarding.permissions.PermissionManager
import com.flowspeed.link.android.util.FlowAppManager
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StartOnBootBroadcastReceiver : BroadcastReceiver(), KoinComponent {
    private val appManager: FlowAppManager by inject()
    private val appSettingStorage: BaseAppSettingsStorage by inject()
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (appSettingStorage.autoStartOnBoot.value) {
                appManager.bootDownloadSystemAndService()
            }
        }
    }
}
