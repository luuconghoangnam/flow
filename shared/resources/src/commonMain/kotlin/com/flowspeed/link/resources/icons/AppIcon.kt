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
            // Background - dark
            path(fill = SolidColor(Color(0xFF0A0A0A))) {
                moveTo(0f, 0f)
                lineTo(48f, 0f)
                lineTo(48f, 48f)
                lineTo(0f, 48f)
                close()
            }
            // Diamond shape - outer
            path(fill = SolidColor(Color(0xFF586F9D))) {
                // Top point
                moveTo(24f, 2f)
                // Right point
                lineTo(46f, 24f)
                // Bottom point
                lineTo(24f, 46f)
                // Left point
                lineTo(2f, 24f)
                close()
            }
            // Inner diamond - darker
            path(fill = SolidColor(Color(0xFF253050))) {
                moveTo(24f, 6f)
                lineTo(42f, 24f)
                lineTo(24f, 42f)
                lineTo(6f, 24f)
                close()
            }
            // Center V-shape / chevron pattern (like the logo)
            path(fill = SolidColor(Color(0xFFB3BCCF))) {
                // Left arm of V
                moveTo(12f, 14f)
                lineTo(15f, 14f)
                lineTo(24f, 32f)
                lineTo(21f, 32f)
                close()
            }
            path(fill = SolidColor(Color(0xFFB3BCCF))) {
                // Right arm of V
                moveTo(33f, 14f)
                lineTo(36f, 14f)
                lineTo(27f, 32f)
                lineTo(24f, 32f)
                close()
            }
            // Horizontal bars (speed lines)
            path(fill = SolidColor(Color(0xFF8090B0))) {
                moveTo(10f, 22f)
                lineTo(18f, 22f)
                lineTo(18f, 23.5f)
                lineTo(10f, 23.5f)
                close()
            }
            path(fill = SolidColor(Color(0xFF8090B0))) {
                moveTo(30f, 22f)
                lineTo(38f, 22f)
                lineTo(38f, 23.5f)
                lineTo(30f, 23.5f)
                close()
            }
            path(fill = SolidColor(Color(0xFF8090B0))) {
                moveTo(11f, 25.5f)
                lineTo(19f, 25.5f)
                lineTo(19f, 27f)
                lineTo(11f, 27f)
                close()
            }
            path(fill = SolidColor(Color(0xFF8090B0))) {
                moveTo(29f, 25.5f)
                lineTo(37f, 25.5f)
                lineTo(37f, 27f)
                lineTo(29f, 27f)
                close()
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
