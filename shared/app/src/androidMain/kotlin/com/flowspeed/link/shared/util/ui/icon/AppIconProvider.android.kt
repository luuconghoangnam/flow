package com.flowspeed.link.shared.util.ui.icon

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath

actual object AppIconProvider {
    private var _bitmap: ImageBitmap? = null

    actual fun getImageBitmap(): ImageBitmap {
        if (_bitmap == null) {
            val bytes = FileSystem.RESOURCES.read("com/flowspeed/link/resources/app_icon.png".toPath()) {
                readByteArray()
            }
            val androidBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            _bitmap = androidBitmap.asImageBitmap()
        }
        return _bitmap!!
    }
}
