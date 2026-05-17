package com.flowspeed.link.desktop.pages.extenallibs

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.desktop.window.custom.WindowTitle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.resources.myStringResource

@Composable
fun ShowOpenSourceLibraries(appComponent: AppComponent){
    ShowOpenSourceLibraries(
        visible = appComponent.showOpenSourceLibraries.collectAsState().value,
        onRequestClose = {
            appComponent.closeOpenSourceLibraries()
        }
    )
}

@Composable
fun ShowOpenSourceLibraries(
    visible: Boolean,
    onRequestClose:()->Unit,
) {
    if (!visible) return
    CustomWindow(
        onCloseRequest = onRequestClose,
        state = rememberWindowState(
            size = DpSize(650.dp, 400.dp)
        )
    ) {
        WindowTitle("Third-Party Libraries")
        ExternalLibsPage()
    }
}