package com.flowspeed.link.shared.ui.widget.menu.custom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import com.flowspeed.link.shared.ui.widget.rememberMyComponentRectPositionProvider
import com.flowspeed.lib.util.compose.action.MenuItem

/**
 * TODO (KMP) implement it based on design
 * it's our context menu!
 */
@Composable
actual fun ShowOptionsInPopup(
    menu: MenuItem.SubMenu,
    onDismissRequest: () -> Unit
) {
    Popup(
        popupPositionProvider = rememberMyComponentRectPositionProvider(
            alignment = Alignment.BottomEnd
        ),
        onDismissRequest = onDismissRequest
    ) {
        RenderOptions(menu, onDismissRequest)
    }
}
