package com.flowspeed.link.shared.ui.configurable.item

import com.flowspeed.link.shared.ui.configurable.BaseEnumConfigurable
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.link.shared.ui.configurable.defaultValueToString
import com.flowspeed.lib.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class EnumConfigurable<T>(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<T>,
    describe: ((T) -> StringSource),
    possibleValues: List<T>,
    valueToString: (T) -> List<String> = ::defaultValueToString,
    val renderMode: RenderMode = RenderMode.Spinner,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : BaseEnumConfigurable<T>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = describe,
    possibleValues = possibleValues,
    valueToString = valueToString,
    enabled = enabled,
    visible = visible,
) {
    object Key : Configurable.Key

    override fun getKey() = Key

    enum class RenderMode {
        Spinner,
    }
}
