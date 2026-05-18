/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.shared.ui.widget

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Animated grid background with subtle pulse effect.
 * Creates a "living" blueprint feel - lines pulse gently.
 */
@Composable
fun CyberGridBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.03f,
        targetValue = 0.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier) {
        val step = 24.dp.toPx()
        val lineColor = Color.White.copy(alpha = pulseAlpha)
        val accentColor = Color(0xFFE64A00).copy(alpha = pulseAlpha * 0.5f)
        val width = size.width
        val height = size.height

        // Regular grid lines
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

        // Accent cross at center
        val cx = width / 2f
        val cy = height / 2f
        drawLine(
            color = accentColor,
            start = Offset(cx - 40.dp.toPx(), cy),
            end = Offset(cx + 40.dp.toPx(), cy),
            strokeWidth = 1f
        )
        drawLine(
            color = accentColor,
            start = Offset(cx, cy - 40.dp.toPx()),
            end = Offset(cx, cy + 40.dp.toPx()),
            strokeWidth = 1f
        )
    }
}
