package com.flowspeed.link.shared.ui.configurable.item

import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.lib.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalTime

class TimeConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<LocalTime>,
    describe: (LocalTime) -> StringSource,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<LocalTime>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = describe,
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key
}
