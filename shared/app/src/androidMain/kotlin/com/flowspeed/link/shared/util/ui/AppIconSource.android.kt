package com.flowspeed.link.shared.util.ui

import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.link.resources.icons.AppIcon
import com.flowspeed.link.resources.icons.FlowIcons

actual fun AppIconSource(): IconSource {
    return IconSource.VectorIconSource(
        value = FlowIcons.AppIcon,
        requiredTint = false,
        uri = "icon:appIcon"
    )
}
