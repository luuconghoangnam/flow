/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.shared.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.link.shared.util.convertPositiveSpeedToHumanReadable
import com.flowspeed.link.shared.util.convertPositiveBytesToHumanReadable
import com.flowspeed.lib.util.datasize.CommonSizeConvertConfigs

/**
 * A card representing a single download item in the grid.
 * Cyber-Industrial style: sharp corners, orange progress bar, high contrast.
 */
@Composable
fun DownloadCard(
    item: IDownloadItemState,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) myColors.primary else myColors.onBackground / 0.1f
    val bgColor = if (isSelected) myColors.primary / 0.05f else myColors.surface

    Column(
        modifier = modifier
            .border(1.dp, borderColor, RectangleShape)
            .background(bgColor, RectangleShape)
            .clip(RectangleShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        // Filename
        Text(
            text = item.name,
            fontSize = myTextSizes.base,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))

        // Progress bar with glow effect for active downloads
        val targetProgress = when (item) {
            is ProcessingDownloadItemState -> {
                if (item.contentLength > 0) item.progress.toFloat() / item.contentLength.toFloat()
                else 0f
            }
            is CompletedDownloadItemState -> 1f
            else -> 0f
        }
        val progress by animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(500, easing = LinearOutSlowInEasing),
            label = "progress"
        )
        val isActive = item is ProcessingDownloadItemState
        val targetColor = if (item is CompletedDownloadItemState) myColors.success else myColors.primary
        val progressColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(500),
            label = "progressColor"
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(myColors.onBackground / 0.1f, RectangleShape)
                .semantics {
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress, 0f..1f)
                }
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(progressColor, RectangleShape)
            )
            // Shimmer overlay for active downloads
            if (isActive && progress > 0f) {
                val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
                val shimmerOffset by shimmerTransition.animateFloat(
                    initialValue = -1f,
                    targetValue = 2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmer_offset"
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.2f),
                                    Color.Transparent,
                                ),
                                startX = shimmerOffset * 200f,
                                endX = (shimmerOffset + 0.5f) * 200f,
                            ),
                            RectangleShape
                        )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Progress percentage
        WithContentAlpha(0.6f) {
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = myTextSizes.xs,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Speed + Size row
        val stateCategory = when(item) {
            is ProcessingDownloadItemState -> 0
            is CompletedDownloadItemState -> 1
            else -> 2
        }

        AnimatedContent(
            targetState = stateCategory,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "speedSizeTransition"
        ) { category ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                when (category) {
                    0 -> {
                        val processingItem = item as? ProcessingDownloadItemState
                        // Speed
                        Text(
                            text = formatSpeedCompact(processingItem?.speed ?: 0L),
                            fontSize = myTextSizes.sm,
                            color = myColors.primary,
                        )
                        // Size
                        Text(
                            text = formatSizeCompact(item.contentLength),
                            fontSize = myTextSizes.sm,
                        )
                    }
                    1 -> {
                        Text(
                            text = "✓",
                            fontSize = myTextSizes.sm,
                            color = myColors.success,
                        )
                        Text(
                            text = formatSizeCompact(item.contentLength),
                            fontSize = myTextSizes.sm,
                        )
                    }
                    else -> {
                        Text(
                            text = "—",
                            fontSize = myTextSizes.sm,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpeedCompact(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    return convertPositiveSpeedToHumanReadable(bytesPerSecond, CommonSizeConvertConfigs.BinaryBytes)
}

private fun formatSizeCompact(bytes: Long): String {
    if (bytes <= 0) return "—"
    return convertPositiveBytesToHumanReadable(bytes, CommonSizeConvertConfigs.BinaryBytes) ?: "—"
}
