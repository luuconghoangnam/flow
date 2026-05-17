package com.flowspeed.link.shared.ui.widget

import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.flowspeed.link.resources.Res
import com.flowspeed.lib.util.compose.resources.myStringResource

/**
 * New Download button - Cyber-Industrial style.
 * Sharp corners, orange border, no icon clutter.
 */
@Composable
fun AddUrlButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .border(1.dp, SolidColor(myColors.primary), RectangleShape)
            .background(myColors.primary.copy(alpha = 0.1f), RectangleShape)
            .clickable(onClick = onClick)
            .height(30.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WithContentAlpha(1f) {
            Text(
                myStringResource(Res.string.new_download),
                Modifier,
                maxLines = 1,
                fontSize = myTextSizes.sm,
                color = myColors.primary,
            )
        }
    }
}
