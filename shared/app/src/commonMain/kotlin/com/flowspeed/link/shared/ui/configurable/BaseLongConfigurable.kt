package com.flowspeed.link.shared.ui.configurable

import com.flowspeed.lib.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class BaseLongConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<Long>,
    describe: ((Long) -> StringSource),
    val range: LongRange,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<Long>(
    title = title,
    description = description,
    backedBy = backedBy,
    validate = {
        it in range
    },
    describe = describe,
    enabled = enabled,
    visible = visible,
)
