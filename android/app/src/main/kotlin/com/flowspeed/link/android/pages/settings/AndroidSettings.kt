package com.flowspeed.link.android.pages.settings

import com.flowspeed.link.android.pages.onboarding.permissions.FlowPermissions
import com.flowspeed.link.android.storage.AppSettingsStorage
import com.flowspeed.link.android.ui.configurable.android.item.PermissionConfigurable
import com.flowspeed.link.android.util.pagemanager.PermissionsPageManager
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.configurable.item.BooleanConfigurable
import com.flowspeed.link.shared.ui.configurable.item.NavigatableConfigurable
import com.flowspeed.lib.util.compose.asStringSource
import kotlinx.coroutines.flow.MutableStateFlow


object AndroidSettings {
    fun permissionSettings(
        permissionsPageManager: PermissionsPageManager
    ): NavigatableConfigurable {
        return NavigatableConfigurable(
            title = Res.string.permissions.asStringSource(),
            description = "".asStringSource(),
            onRequestNavigate = {
                permissionsPageManager.openPermissionsPage(false)
            },
        )
    }

    fun ignoreBatteryOptimizations(): PermissionConfigurable {
        val permission = FlowPermissions.BatteryOptimizationPermission
        return PermissionConfigurable(
            title = permission.title,
            description = permission.description,
            backedBy = MutableStateFlow(permission),
        )
    }

    fun browserIconInLauncher(
        appSettingsStorage: AppSettingsStorage
    ): BooleanConfigurable {
        return BooleanConfigurable(
            title = Res.string.settings_browser_in_launcher.asStringSource(),
            description = Res.string.settings_browser_in_launcher_description.asStringSource(),
            backedBy = appSettingsStorage.browserIconInLauncher,
            describe = {
                if (it) {
                    Res.string.enabled
                } else {
                    Res.string.disabled
                }.asStringSource()
            }
        )
    }
}
