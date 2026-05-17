package com.flowspeed.link.desktop.pages.home

import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.lib.util.ifThen
import com.flowspeed.link.shared.ui.widget.menu.custom.MyDropDown
import com.flowspeed.link.shared.ui.widget.menu.custom.SubMenu
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.link.shared.util.ui.WithContentColor
import com.flowspeed.lib.util.compose.action.MenuItem
import com.flowspeed.link.shared.util.div
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import com.flowspeed.link.shared.ui.widget.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.flowspeed.link.shared.ui.widget.Tooltip
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource

/**
 * Compact action toolbar - Cyber-Industrial style.
 * Icons only, tight spacing, sharp separators.
 */
@Composable
fun Actions(
    list: List<MenuItem>,
    showLabels: Boolean,
) {
    Row(
        Modifier.height(IntrinsicSize.Max),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (a in list) {
            when (a) {
                MenuItem.Separator -> {
                    Spacer(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                            .width(1.dp)
                            .background(myColors.onBackground / 0.1f)
                    )
                }

                is MenuItem.SingleItem -> {
                    CompactActionButton(a)
                }

                is MenuItem.SubMenu -> {
                    CompactGroupButton(a)
                }
            }
        }
    }
}

@Composable
private fun CompactActionButton(
    action: MenuItem.SingleItem,
) {
    val enabled by action.isEnabled.collectAsState()
    val title = action.title.collectAsState().value
    Tooltip(title) {
        Box(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = { action() })
                .ifThen(!enabled) { alpha(0.4f) }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            WithContentColor(myColors.onBackground) {
                action.icon.collectAsState().value?.let { icon ->
                    MyIcon(
                        icon = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactGroupButton(
    action: MenuItem.SubMenu,
) {
    val enabled by action.isEnabled.collectAsState()
    var showSubMenu by remember { mutableStateOf(false) }
    val title = action.title.collectAsState().value
    Tooltip(title) {
        Box(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = { showSubMenu = !showSubMenu })
                .ifThen(!enabled) { alpha(0.4f) }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            WithContentColor(myColors.onBackground) {
                action.icon.collectAsState().value?.let { icon ->
                    MyIcon(
                        icon = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    if (enabled && showSubMenu) {
        MyDropDown(onDismissRequest = { showSubMenu = false }) {
            val items by action.items.collectAsState()
            SubMenu(subMenu = items, onRequestClose = { showSubMenu = false })
        }
    }
}
