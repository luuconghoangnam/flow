package com.flowspeed.link.desktop.pages.enterurl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.desktop.window.custom.WindowTitle
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.util.mvi.HandleEffects
import com.flowspeed.link.shared.util.rememberChild
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.lib.util.desktop.screen.applyUiScale

@Composable
fun EnterNewDownloadWindow(
    appComponent: AppComponent
) {
    val child = appComponent.enterNewURLWindowSlot.rememberChild()
    child?.let {
        EnterNewDownloadWindow(child)
    }
}

@Composable
private fun EnterNewDownloadWindow(
    component: DesktopEnterNewURLComponent,
) {
    val windowState = rememberWindowState(
        size = DpSize(400.dp, 150.dp)
            .applyUiScale(LocalUiScale.current),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    CustomWindow(
        state = windowState,
        onCloseRequest = component::close
    ) {
        WindowTitle(
            myStringResource(Res.string.new_download)
        )
        HandleEffects(component) {
            when (it) {
                DesktopEnterNewURLComponent.Effects.BringToFront -> {
                    windowState.isMinimized = false
                    window.toFront()
                }
                else -> {}
            }
        }
        EnterNewURLPage(
            component,
        )
    }
}

