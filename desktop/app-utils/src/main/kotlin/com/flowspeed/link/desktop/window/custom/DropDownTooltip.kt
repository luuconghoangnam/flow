package com.flowspeed.link.desktop.window.custom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.Tooltip
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource

@Composable
private fun SystemButtonTooltip(
    stringSource: StringSource,
    content: @Composable () -> Unit,
) {
    Tooltip(
        tooltip = stringSource,
        anchor = Alignment.BottomCenter,
        alignment = Alignment.BottomCenter,
        content = content,
    )
}

@Composable
internal fun WindowCloseButtonTooltip(
    content: @Composable () -> Unit
) {
    SystemButtonTooltip(
        stringSource = Res.string.window_close.asStringSource(),
    ) {
        content()
    }
}

@Composable
internal fun WindowToggleMaximizeTooltip(
    content: @Composable () -> Unit
) {
    SystemButtonTooltip(
        stringSource = if (isWindowMaximized()) {
            Res.string.window_restore
        } else {
            Res.string.window_maximize
        }.asStringSource(),
    ) {
        content()
    }
}

@Composable
internal fun WindowMinimizeTooltip(
    content: @Composable () -> Unit
) {
    SystemButtonTooltip(
        stringSource = Res.string.window_minimize.asStringSource(),
    ) {
        content()
    }
}
