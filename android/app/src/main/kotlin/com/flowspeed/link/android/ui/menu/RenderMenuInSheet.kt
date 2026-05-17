package com.flowspeed.link.android.ui.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.flowspeed.link.android.ui.SheetHeader
import com.flowspeed.link.android.ui.SheetTitle
import com.flowspeed.link.android.ui.SheetUI
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.TransparentIconActionButton
import com.flowspeed.link.shared.util.OnFullyDismissed
import com.flowspeed.link.shared.util.ResponsiveDialog
import com.flowspeed.link.shared.util.ResponsiveDialogScope
import com.flowspeed.link.shared.util.rememberResponsiveDialogState
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.util.compose.action.MenuItem
import com.flowspeed.lib.util.compose.asStringSource

@Composable
private fun ResponsiveDialogScope.RenderMenuInSheetUi(
    menuStack: StackMenuState,
    onDismissRequest: () -> Unit,
) {
    val currentMenu = menuStack.currentMenu
    SheetUI(
        header = {
            SheetHeader(
                headerTitle = {
                    SheetTitle(
                        title = currentMenu.title.collectAsState().value.rememberString(),
                        icon = currentMenu.icon.collectAsState().value,
                    )
                },
                headerActions = {
                    if (menuStack.canGoBack) {
                        TransparentIconActionButton(
                            icon = MyIcons.back,
                            contentDescription = Res.string.back.asStringSource(),
                        ) {
                            menuStack.pop()
                        }
                    }
                    TransparentIconActionButton(
                        MyIcons.close,
                        Res.string.close.asStringSource()
                    ) {
                        onDismissRequest()
                    }
                }
            )
        }
    ) {
        BaseStackedMenu(
            menuStack = menuStack,
            onDismissRequest = onDismissRequest,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun RenderMenuInSheet(
    menu: MenuItem.SubMenu?,
    onDismissRequest: () -> Unit,
) {
    val responsiveDialogState = rememberResponsiveDialogState(false)
    LaunchedEffect(menu) {
        if (menu != null) {
            responsiveDialogState.show()
        } else {
            responsiveDialogState.hide()
        }
    }
    responsiveDialogState.OnFullyDismissed {
        onDismissRequest()
    }
    val hideDialog = responsiveDialogState::hide
    menu?.let {
        ResponsiveDialog(
            responsiveDialogState,
            hideDialog,
        ) {
            val menuStackState = rememberMenuStack(it)
            RenderMenuInSheetUi(menuStackState, hideDialog)
        }
    }
}
