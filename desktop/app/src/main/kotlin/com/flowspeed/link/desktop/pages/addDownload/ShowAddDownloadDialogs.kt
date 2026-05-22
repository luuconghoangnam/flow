package com.flowspeed.link.desktop.pages.adddownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.desktop.DesktopAddDownloadDialogManager
import com.flowspeed.link.desktop.pages.adddownload.multiple.DesktopAddMultiDownloadComponent
import com.flowspeed.link.desktop.pages.adddownload.multiple.AddMultiItemPage
import com.flowspeed.link.desktop.pages.adddownload.single.AddDownloadPage
import com.flowspeed.link.shared.pages.adddownload.single.BaseAddSingleDownloadComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.desktop.window.custom.WindowIcon
import com.flowspeed.link.desktop.window.custom.WindowTitle
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.pages.adddownload.AddDownloadComponent
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.lib.util.desktop.PlatformAppActivator
import com.flowspeed.lib.util.desktop.screen.applyUiScale
import java.awt.Dimension

@Composable
fun ShowAddDownloadDialogs(component: DesktopAddDownloadDialogManager) {
    val openedAddDownloadDialogs = component.openedAddDownloadDialogs.collectAsState().value
    for (addDownloadComponent in openedAddDownloadDialogs) {
        key(addDownloadComponent.id) {
            AddDownloadWindow(
                addDownloadComponent = addDownloadComponent,
                onRequestClose = {
                    component.closeAddDownloadDialog(addDownloadComponent.id)
                }
            )
        }
    }
}

@Composable
private fun AddDownloadWindow(
    addDownloadComponent: AddDownloadComponent,
    onRequestClose: () -> Unit,
) {
    val shouldShowWindow by addDownloadComponent.shouldShowWindow.collectAsState()
    if (!shouldShowWindow) return
    val uiScale = LocalUiScale.current
    when (addDownloadComponent) {
        is BaseAddSingleDownloadComponent -> {
            val h = 265.applyUiScale(uiScale)
            val w = 500.applyUiScale(uiScale)
            val size = remember {
                DpSize(
                    height = h.dp,
                    width = w.dp,
                )
            }

            val state = rememberWindowState(
                size = size,
                position = WindowPosition(Alignment.Center)
            )
            CustomWindow(
                state = state,
                onCloseRequest = onRequestClose,
                alwaysOnTop = true,
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(w, h)
                    window.toFront()
                    window.requestFocus()
                    kotlinx.coroutines.delay(100)
                    PlatformAppActivator.active()
                }
//                    BringToFront()
                WindowTitle(myStringResource(Res.string.add_download))
                WindowIcon(MyIcons.appIcon)
                AddDownloadPage(addDownloadComponent)
            }
        }

        is DesktopAddMultiDownloadComponent -> {
            val h = 450
            val w = 800
            val state = rememberWindowState(
                height = h.dp,
                width = w.dp,
                position = WindowPosition(Alignment.Center)
            )
            CustomWindow(
                state = state,
                onCloseRequest = onRequestClose,
                alwaysOnTop = true,
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(w, h)
                    window.toFront()
                    window.requestFocus()
                    kotlinx.coroutines.delay(100)
                    PlatformAppActivator.active()
                }
//                    BringToFront()
                WindowTitle(myStringResource(Res.string.add_download))
                WindowIcon(MyIcons.appIcon)
                AddMultiItemPage(addDownloadComponent)
            }
        }
    }
}

//it seems not affect at all
//@Composable
//private fun WindowScope.BringToFront() {
//    LaunchedEffect(Unit) {
//        window.toFront()
//        window.requestFocus()
//    }
//}
