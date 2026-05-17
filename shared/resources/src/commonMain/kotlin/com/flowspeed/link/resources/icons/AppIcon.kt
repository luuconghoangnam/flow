package com.flowspeed.link.resources.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import okio.FileSystem
import okio.Path.Companion.toPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path

private const val APP_ICON_RESOURCE = "com/flowspeed/link/resources/app_icon.png"

/**
 * App icon loaded from PNG resource.
 * This is a simple placeholder vector that matches the logo shape.
 * The actual PNG icon is used for window/tray icons via icon.ico/icon.png files.
 */
val FlowIcons.AppIcon: ImageVector
    get() {
        if (_AppIcon != null) {
            return _AppIcon!!
        }
        _AppIcon = ImageVector.Builder(
            name = "AppIcon",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            // Transparent background
            path(fill = SolidColor(Color.Transparent)) {
                moveTo(0f, 0f)
                lineTo(48f, 0f)
                lineTo(48f, 48f)
                lineTo(0f, 48f)
                close()
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
