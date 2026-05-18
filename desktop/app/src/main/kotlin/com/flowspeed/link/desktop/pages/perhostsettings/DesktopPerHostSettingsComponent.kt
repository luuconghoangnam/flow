package com.flowspeed.link.desktop.pages.perhostsettings

import com.flowspeed.link.shared.pages.perhostsettings.BasePerHostSettingsComponent
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.util.perhostsettings.PerHostSettingsManager
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope

class DesktopPerHostSettingsComponent(
    ctx: ComponentContext,
    perHostSettingsManager: PerHostSettingsManager,
    appRepository: BaseAppRepository,
    appScope: CoroutineScope,
    closeRequested: () -> Unit,
) : BasePerHostSettingsComponent(
    ctx = ctx,
    perHostSettingsManager = perHostSettingsManager,
    appRepository = appRepository,
    appScope = appScope,
    closeRequested = closeRequested,
) {
    data class Config(
        override val openedHost: String?
    ) : BasePerHostSettingsComponent.Config

    fun bringToFront() {
        sendEffect(Effects.BringToFront)
    }

    sealed interface Effects : BasePerHostSettingsComponent.Effects.Platform {
        data object BringToFront : Effects
    }
}
