package com.flowspeed.link.shared.util.ui.icon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.flowspeed.lib.util.compose.IconSource

expect object AppIconProvider {
    fun getImageBitmap(): ImageBitmap
}

fun getAppIconSource(): IconSource {
    return IconSource.PainterIconSource(
        value = BitmapPainter(AppIconProvider.getImageBitmap()),
        requiredTint = false,
        uri = "icon:appIcon"
    )
}
