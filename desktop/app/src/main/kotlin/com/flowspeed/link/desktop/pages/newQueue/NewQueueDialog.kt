package com.flowspeed.link.desktop.pages.newqueue

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.lib.util.desktop.screen.applyUiScale

@Composable
fun NewQueueDialog(
    appComponent: AppComponent,
) {
    if (appComponent.showCreateQueueDialog.collectAsState().value){
        CustomWindow(
            state = rememberWindowState(
                size = DpSize(width = 300.dp, height = 130.dp)
                    .applyUiScale(LocalUiScale.current),
                position = WindowPosition.Aligned(Alignment.Center),
            ),
            resizable = false,
            onRequestToggleMaximize = null,
            onRequestMinimize = null,
            alwaysOnTop = true,
            onCloseRequest = {
                appComponent.closeNewQueueDialog()
            }
        ) {
            NewQueue(
                onQueueCreate = {
                    appComponent.closeNewQueueDialog()
                    appComponent.createNewQueue(it)
                },
                onCloseRequest = {
                    appComponent.closeNewQueueDialog()
                }
            )
        }
    }
}
