package com.flowspeed.link.desktop.pages.updater

import com.flowspeed.link.desktop.window.custom.CustomWindow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.link.shared.pages.updater.RenderUpdateNotifications
import com.flowspeed.link.shared.pages.updater.UpdateComponent
import com.flowspeed.lib.util.desktop.screen.applyUiScale

@Composable
fun ShowUpdaterDialog(updaterComponent: UpdateComponent) {
    val showUpdate = updaterComponent.showNewUpdate.collectAsState().value
    val newVersion = updaterComponent.newVersionData.collectAsState().value
    val closeUpdatePage = {
        updaterComponent.requestClose()
    }
    RenderUpdateNotifications(updaterComponent)
    if (showUpdate && newVersion != null) {
        val uiScale = LocalUiScale.current
        CustomWindow(
            state = rememberWindowState(
                size = DpSize(500.dp, 400.dp).applyUiScale(uiScale),
                position = WindowPosition.Aligned(Alignment.Center)
            ),
            onCloseRequest = closeUpdatePage,
        ) {
            NewUpdatePage(
                newVersionInfo = newVersion,
                currentVersion = updaterComponent.currentVersion,
                cancel = closeUpdatePage,
                update = {
                    updaterComponent.performUpdate()
                    closeUpdatePage()
                }
            )
        }
    }
}
