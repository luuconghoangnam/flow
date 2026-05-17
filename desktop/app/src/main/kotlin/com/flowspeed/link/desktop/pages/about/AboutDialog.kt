package com.flowspeed.link.desktop.pages.about

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.desktop.window.custom.WindowTitle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.desktop.window.custom.WindowIcon
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.lib.util.desktop.screen.applyUiScale

@Composable
fun ShowAboutDialog(appComponent: AppComponent) {
    if (appComponent.showAboutPage.collectAsState().value) {
        AboutDialog(
            onClose = {
                appComponent.closeAbout()
            },
            onRequestShowOpenSourceLibraries = {
                appComponent.openOpenSourceLibrariesPage()
            },
            onRequestShowTranslators = {
                appComponent.openTranslatorsPage()
            }
        )
    }
}

@Composable
fun AboutDialog(
    onClose: () -> Unit,
    onRequestShowOpenSourceLibraries: () -> Unit,
    onRequestShowTranslators: () -> Unit,
) {
    CustomWindow(
        resizable = false,
        onRequestToggleMaximize = null,
        alwaysOnTop = false,
        onRequestMinimize = null,
        state = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(600.dp, 310.dp)
                .applyUiScale(LocalUiScale.current)
        ),
        onCloseRequest = onClose
    ) {
        WindowTitle(myStringResource(Res.string.about))
        WindowIcon(MyIcons.info)
        AboutPage(
            onRequestShowOpenSourceLibraries = onRequestShowOpenSourceLibraries,
            onRequestShowTranslators = onRequestShowTranslators
        )
    }
}
