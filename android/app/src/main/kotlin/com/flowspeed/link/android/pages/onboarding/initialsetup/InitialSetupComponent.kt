package com.flowspeed.link.android.pages.onboarding.initialsetup

import com.flowspeed.link.shared.settings.CommonSettings
import com.flowspeed.link.shared.ui.configurable.ConfigurableGroup
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.link.shared.util.BaseComponent
import com.arkivanov.decompose.ComponentContext
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager

class InitialSetupComponent(
    ctx: ComponentContext,
    private val languageManager: LanguageManager,
    private val themeManager: ThemeManager,
    private val onFinish: () -> Unit
) : BaseComponent(ctx) {
    val configurables = listOf(
            CommonSettings.languageConfig(languageManager, scope),
            CommonSettings.themeConfig(themeManager, scope),
        )

    fun onUserPressFinish() {
        onFinish()
    }
}
