package com.flowspeed.link.desktop.pages.perhostsettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.shared.pages.perhostsettings.BasePerHostSettingsComponent
import com.flowspeed.link.shared.util.mvi.HandleEffects
import com.flowspeed.link.shared.util.rememberChild

@Composable
fun PerHostSettingsWindow(
    appComponent: AppComponent
) {
    val component = appComponent.perHostSettingsSlot.rememberChild()
    if (component != null) {
        val windowState = rememberWindowState(
            size = DpSize(
                600.dp,
                400.dp,
            ),
            position = WindowPosition.Aligned(Alignment.Center)
        )
        CustomWindow(
            state = windowState,
            onCloseRequest = appComponent::closePerHostSettings,
        ) {
            HandleEffects(component) {
                when (it) {
                    is BasePerHostSettingsComponent.Effects.Platform -> {
                        when (it as DesktopPerHostSettingsComponent.Effects) {
                            DesktopPerHostSettingsComponent.Effects.BringToFront -> {
                                windowState.isMinimized = false
                                window.toFront()
                            }
                        }
                    }
                }
            }
            PerHostSettingsPage(component)
        }
    }
}
