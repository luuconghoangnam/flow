package com.flowspeed.link.shared.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

        // Progress bar
        val progress = when (item) {
            is ProcessingDownloadItemState -> {
                if (item.contentLength > 0) item.progress.toFloat() / item.contentLength.toFloat()
                else 0f
            }
            is CompletedDownloadItemState -> 1f
            else -> 0f
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(myColors.onBackground / 0.1f, RectangleShape)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(myColors.primary, RectangleShape)
            )
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when (item) {
                is ProcessingDownloadItemState -> {
                    // Speed
                    Text(
                        text = formatSpeedCompact(item.speed),
                        fontSize = myTextSizes.sm,
                        color = myColors.primary,
                    )
                    // Size
                    Text(
                        text = formatSizeCompact(item.contentLength),
                        fontSize = myTextSizes.sm,
                    )
                }
                is CompletedDownloadItemState -> {
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

private fun formatSpeedCompact(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    return convertPositiveSpeedToHumanReadable(bytesPerSecond, CommonSizeConvertConfigs.BinaryBytes)
}

private fun formatSizeCompact(bytes: Long): String {
    if (bytes <= 0) return "—"
    return convertPositiveBytesToHumanReadable(bytes, CommonSizeConvertConfigs.BinaryBytes) ?: "—"
}
