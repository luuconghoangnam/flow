package com.flowspeed.link.desktop.pages.batchdownload

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.shared.pages.batchdownload.BaseBatchDownloadComponent
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.link.shared.util.mvi.HandleEffects
import com.flowspeed.lib.util.desktop.screen.applyUiScale

@Composable
fun BatchDownloadWindow(desktopBatchDownloadComponent: DesktopBatchDownloadComponent) {
    CustomWindow(
        state = rememberWindowState(
            size = DpSize(500.dp, 420.dp)
                .applyUiScale(LocalUiScale.current),
            position = WindowPosition(Alignment.Center)
        ),
        onCloseRequest = desktopBatchDownloadComponent.onClose
    ) {
        HandleEffects(desktopBatchDownloadComponent) {
            when (it) {
                DesktopBatchDownloadComponent.Effects.BringToFront -> window.toFront()
                is BaseBatchDownloadComponent.Effects.PlatformEffects -> {
                    //
                }
            }
        }
        BatchDownload(desktopBatchDownloadComponent)
    }
}
