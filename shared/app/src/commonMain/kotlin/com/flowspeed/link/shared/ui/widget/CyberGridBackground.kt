package com.flowspeed.link.shared.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Subtle grid background pattern for the Cyber-Industrial aesthetic.
 * Draws thin lines at regular intervals to create a blueprint-like depth effect.
 */
@Composable
fun CyberGridBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 24.dp.toPx()
        val lineColor = Color(0x08FFFFFF)
        val width = size.width
        val height = size.height

        // Vertical lines
        var x = 0f
        while (x < width) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 0.5f
            )
            x += step
        }

        // Horizontal lines
        var y = 0f
        while (y < height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 0.5f
            )
            y += step
        }
    }
}
