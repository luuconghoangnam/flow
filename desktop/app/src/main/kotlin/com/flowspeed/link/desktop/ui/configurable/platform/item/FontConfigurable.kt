package com.flowspeed.link.desktop.ui.configurable.platform.item

import com.flowspeed.link.desktop.pages.settings.FontInfo
import com.flowspeed.link.shared.ui.configurable.BaseEnumConfigurable
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.lib.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FontConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<FontInfo>,
    describe: (FontInfo) -> StringSource,
    possibleValues: List<FontInfo>,
    valueToString: (FontInfo) -> List<String> = {
        listOf(it.name.getString())
    },
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : BaseEnumConfigurable<FontInfo>(
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
}
