package com.flowspeed.lib.util.compose.action

import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource

abstract class AnAction(
    title: StringSource,
    icon: IconSource? = null,
) : MenuItem.SingleItem(
    title = title,
    icon = icon,
) {
    override fun onClick() = actionPerformed()

    abstract fun actionPerformed()
}


