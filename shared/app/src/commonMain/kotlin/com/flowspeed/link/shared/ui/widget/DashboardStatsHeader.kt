package com.flowspeed.link.shared.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.link.shared.util.convertPositiveSpeedToHumanReadable
import com.flowspeed.link.shared.util.convertPositiveBytesToHumanReadable
import com.flowspeed.lib.util.datasize.CommonSizeConvertConfigs

/**
 * Dashboard stats header showing aggregate download statistics.
 * Displays: total speed, active download count, total disk usage.
 */
@Composable
fun DashboardStatsHeader(
    totalSpeed: Long,
    activeCount: Int,
    totalDiskUsage: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatBox(
            label = myStringResource(Res.string.speed),
            value = formatSpeed(totalSpeed),
            modifier = Modifier.weight(1f),
        )
        StatBox(
            label = "Active",
            value = activeCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatBox(
            label = myStringResource(Res.string.size),
            value = formatSize(totalDiskUsage),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, myColors.onBackground / 0.1f, RectangleShape)
            .background(myColors.surface, RectangleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WithContentAlpha(0.5f) {
            Text(
                text = label,
                fontSize = myTextSizes.xs,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = myTextSizes.lg,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    return convertPositiveSpeedToHumanReadable(bytesPerSecond, CommonSizeConvertConfigs.BinaryBytes)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    return convertPositiveBytesToHumanReadable(bytes, CommonSizeConvertConfigs.BinaryBytes) ?: "0 B"
}
