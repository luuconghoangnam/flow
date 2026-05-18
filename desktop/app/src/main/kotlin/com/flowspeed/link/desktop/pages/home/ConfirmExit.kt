package com.flowspeed.link.desktop.pages.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.ui.widget.ConfirmDialog
import com.flowspeed.link.desktop.ui.widget.ConfirmDialogType
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.asStringSource

@Composable
fun ConfirmExit(appComponent: AppComponent) {
    val showExitDialog by appComponent.showConfirmExitDialog.collectAsState()
    if (showExitDialog) {
        ConfirmDialog(
            Res.string.confirm_exit.asStringSource(),
            Res.string.confirm_exit_description.asStringSource(),
            onCancel = appComponent::closeConfirmExit,
            onConfirm = appComponent::exitAppAsync,
            type = ConfirmDialogType.Warning,
        )
    }
}