package com.flowspeed.link.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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
            // Dark background rounded rect
            path(fill = SolidColor(Color(0xFF0A0A0A))) {
                moveTo(8f, 0f)
                curveTo(3.582f, 0f, 0f, 3.582f, 0f, 8f)
                lineTo(0f, 40f)
                curveTo(0f, 44.418f, 3.582f, 48f, 8f, 48f)
                lineTo(40f, 48f)
                curveTo(44.418f, 48f, 48f, 44.418f, 48f, 40f)
                lineTo(48f, 8f)
                curveTo(48f, 3.582f, 44.418f, 0f, 40f, 0f)
                close()
            }
            // Down arrow shaft
            path(fill = SolidColor(Color(0xFFB3BCCF))) {
                moveTo(22.5f, 12f)
                lineTo(25.5f, 12f)
                lineTo(25.5f, 30f)
                lineTo(22.5f, 30f)
                close()
            }
            // Arrow head
            path(fill = SolidColor(Color(0xFFB3BCCF))) {
                moveTo(24f, 36f)
                lineTo(16f, 28f)
                lineTo(18.2f, 25.8f)
                lineTo(24f, 31.6f)
                lineTo(29.8f, 25.8f)
                lineTo(32f, 28f)
                close()
            }
            // Bottom bar
            path(fill = SolidColor(Color(0xFFB3BCCF))) {
                moveTo(14f, 38f)
                lineTo(34f, 38f)
                lineTo(34f, 40.5f)
                lineTo(14f, 40.5f)
                close()
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
