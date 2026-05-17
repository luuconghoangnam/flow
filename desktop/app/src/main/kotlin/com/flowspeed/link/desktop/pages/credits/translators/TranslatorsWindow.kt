package com.flowspeed.link.desktop.pages.credits.translators

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.desktop.window.custom.WindowTitle
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.resources.myStringResource


@Composable
fun ShowTranslators(
    appComponent: AppComponent,
) {
    TranslatorsWindow(
        isVisible = appComponent.showTranslators.collectAsState().value,
        onRequestClose = {
            appComponent.closeTranslatorsPage()
        }
    )
}

@Composable
private fun TranslatorsWindow(
    isVisible: Boolean,
    onRequestClose: () -> Unit,
) {
    if (!isVisible) return
    CustomWindow(
        onCloseRequest = onRequestClose,
        state = rememberWindowState(
            size = DpSize(650.dp, 500.dp)
        )
    ) {
        WindowTitle(myStringResource(Res.string.meet_the_translators))
        Translators(
            modifier = Modifier.fillMaxSize(),
        )
    }
}