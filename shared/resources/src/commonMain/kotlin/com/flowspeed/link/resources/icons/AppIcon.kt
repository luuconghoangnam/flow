package com.flowspeed.link.resources.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
            // Background circle
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFB8451A),
                        1f to Color(0xFFE8652E)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(48f, 48f)
                )
            ) {
                moveTo(24f, 0f)
                curveTo(37.255f, 0f, 48f, 10.745f, 48f, 24f)
                curveTo(48f, 37.255f, 37.255f, 48f, 24f, 48f)
                curveTo(10.745f, 48f, 0f, 37.255f, 0f, 24f)
                curveTo(0f, 10.745f, 10.745f, 0f, 24f, 0f)
                close()
            }
            // Download arrow - vertical line
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(22f, 12f)
                lineTo(26f, 12f)
                lineTo(26f, 30f)
                lineTo(22f, 30f)
                close()
            }
            // Download arrow - triangle head
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(24f, 36f)
                lineTo(15f, 27f)
                lineTo(18f, 24f)
                lineTo(24f, 30f)
                lineTo(30f, 24f)
                lineTo(33f, 27f)
                close()
            }
            // Bottom tray/bar
            path(
                fill = SolidColor(Color.White)
            ) {
                moveTo(12f, 38f)
                lineTo(36f, 38f)
                lineTo(36f, 41f)
                curveTo(36f, 41.552f, 35.552f, 42f, 35f, 42f)
                lineTo(13f, 42f)
                curveTo(12.448f, 42f, 12f, 41.552f, 12f, 41f)
                close()
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
