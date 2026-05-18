package com.flowspeed.link.android.ui.configurable

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flowspeed.link.android.ui.SheetHeader
import com.flowspeed.link.android.ui.SheetTitle
import com.flowspeed.link.android.ui.SheetUI
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.OnFullyDismissed
import com.flowspeed.link.shared.util.ResponsiveDialog
import com.flowspeed.link.shared.util.rememberResponsiveDialogState
import com.flowspeed.lib.util.compose.StringSource

@Composable
fun ConfigurableSheet(
    title: StringSource,
    isOpened: Boolean,
    onDismiss: () -> Unit,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val dialogState = rememberResponsiveDialogState(isOpened)
    LaunchedEffect(isOpened) {
        when (isOpened) {
            true -> dialogState.show()
            false -> dialogState.hide()
        }
    }
    dialogState.OnFullyDismissed {
        onDismiss()
    }
    ResponsiveDialog(
        state = dialogState,
        onDismiss = dialogState::hide,
    ) {
        SheetUI(
            header = {
                SheetHeader(
                    headerTitle = {
                        SheetTitle(
                            title.rememberString()
                        )
                    },
                    headerActions = headerActions,
                )
            }
        ) {
            content()
        }
    }
}
