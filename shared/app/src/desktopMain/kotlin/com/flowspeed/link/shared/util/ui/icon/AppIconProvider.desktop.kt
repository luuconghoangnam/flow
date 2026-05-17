package com.flowspeed.link.shared.util.ui.icon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import okio.FileSystem
import okio.Path.Companion.toPath

actual object AppIconProvider {
    private var _bitmap: ImageBitmap? = null

    actual fun getImageBitmap(): ImageBitmap {
        if (_bitmap == null) {
            val bytes = FileSystem.RESOURCES.read("com/flowspeed/link/resources/app_icon.png".toPath()) {
                readByteArray()
            }
            _bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
        return _bitmap!!
    }
}
