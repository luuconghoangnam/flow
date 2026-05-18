package com.flowspeed.link.shared.ui.configurable.item

import com.flowspeed.link.shared.ui.configurable.BaseLongConfigurable
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.lib.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpeedLimitConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<Long>,
    describe: (Long) -> StringSource,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : BaseLongConfigurable(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = describe,
    range = 0..Long.MAX_VALUE,
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
