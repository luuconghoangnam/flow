package com.flowspeed.link.shared.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.flowspeed.link.shared.pages.home.category.DefinedStatusCategories
import com.flowspeed.link.shared.pages.home.category.DownloadStatusCategoryFilter
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes

/**
 * Horizontal row of status filter chips.
 * Cyber-Industrial style: sharp rectangles, orange active state.
 */
@Composable
fun StatusFilterRow(
    currentFilter: DownloadStatusCategoryFilter,
    filters: List<DownloadStatusCategoryFilter> = DefinedStatusCategories.values(),
    onFilterSelected: (DownloadStatusCategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { filter ->
            val isActive = filter == currentFilter
            Box(
                modifier = Modifier
                    .background(
                        if (isActive) myColors.primary else myColors.surface,
                        RectangleShape
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.name.rememberString(),
                    fontSize = myTextSizes.sm,
                    color = if (isActive) myColors.onPrimary else myColors.onSurface,
                )
            }
        }
    }
}
