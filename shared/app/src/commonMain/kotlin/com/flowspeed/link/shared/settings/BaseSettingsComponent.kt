package com.flowspeed.link.shared.settings

import com.flowspeed.link.shared.ui.configurable.ConfigurableGroup
import com.flowspeed.link.shared.util.BaseComponent
import com.flowspeed.link.shared.util.mvi.ContainsEffects
import com.flowspeed.link.shared.util.mvi.supportEffects
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.StateFlow

abstract class BaseSettingsComponent(
    context: ComponentContext
) : BaseComponent(
    context
),
    ContainsEffects<BaseSettingsComponent.Effects> by supportEffects() {
    abstract val configurables: StateFlow<List<ConfigurableGroup>>

    sealed interface Effects {
        interface Platform : Effects
    }
}
