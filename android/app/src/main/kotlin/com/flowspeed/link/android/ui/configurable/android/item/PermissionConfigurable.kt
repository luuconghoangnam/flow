package com.flowspeed.link.android.ui.configurable.android.item

import com.flowspeed.link.android.pages.onboarding.permissions.AppPermission
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PermissionConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<AppPermission>,
    describe: () -> StringSource = { "".asStringSource() },
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<AppPermission>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = {
        describe()
    },
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
