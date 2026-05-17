package com.flowspeed.link.android.pages.updater

import androidx.compose.runtime.*
import com.flowspeed.link.shared.pages.updater.RenderUpdateNotifications
import com.flowspeed.link.shared.pages.updater.UpdateComponent
import com.flowspeed.link.shared.util.OnFullyDismissed
import com.flowspeed.link.shared.util.ResponsiveDialog
import com.flowspeed.link.shared.util.rememberResponsiveDialogState

@Composable
fun UpdaterSheet(
    updaterComponent: UpdateComponent,
) {
    ShowUpdaterDialog(updaterComponent)
}

@Composable
private fun ShowUpdaterDialog(updaterComponent: UpdateComponent) {
    val showUpdate = updaterComponent.showNewUpdate.collectAsState().value
    val newVersion = updaterComponent.newVersionData.collectAsState().value
    val closeUpdatePage = {
        updaterComponent.requestClose()
    }
    RenderUpdateNotifications(updaterComponent)
    val isOpened = showUpdate && newVersion != null
    val state = rememberResponsiveDialogState(false)
    LaunchedEffect(isOpened) {
        if (isOpened) {
            state.show()
        } else {
            state.hide()
        }
    }
    state.OnFullyDismissed(closeUpdatePage)
    ResponsiveDialog(
        state, state::hide
    ) {
        newVersion?.let {
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
