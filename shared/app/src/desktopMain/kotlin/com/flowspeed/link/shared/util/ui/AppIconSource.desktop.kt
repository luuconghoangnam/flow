package com.flowspeed.link.shared.util.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.flowspeed.lib.util.compose.IconSource
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image

private const val APP_ICON_PATH = "com/flowspeed/link/resources/app_icon.png"

private val appIconPainter by lazy {
    val bytes = FileSystem.RESOURCES.read(APP_ICON_PATH.toPath()) {
        readByteArray()
    }
    val skiaImage = Image.makeFromEncoded(bytes)
    val bitmap = skiaImage.toComposeImageBitmap()
    BitmapPainter(bitmap)
}

actual fun AppIconSource(): IconSource {
    return IconSource.PainterIconSource(
        value = appIconPainter,
        requiredTint = false,
        uri = "icon:appIcon"
    )
}
