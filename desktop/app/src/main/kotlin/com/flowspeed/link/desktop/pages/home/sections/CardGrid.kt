package com.flowspeed.link.desktop.pages.home.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.onClick
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.dp
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.link.shared.ui.widget.DownloadCard

/**
 * Desktop card grid layout for downloads.
 * Uses adaptive grid sizing with Ctrl+click multi-select and right-click context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardGrid(
    downloadList: List<IDownloadItemState>,
    selectionList: List<Long>,
    onItemSelectionChange: (Long, Boolean) -> Unit,
    onNewSelection: (List<Long>) -> Unit,
    onRequestOpenOption: (IDownloadItemState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = downloadList,
            key = { it.id },
        ) { item ->
            val isSelected = selectionList.contains(item.id)
            Box(
                modifier = Modifier
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = {
                            if (!isSelected) {
                                onNewSelection(listOf(item.id))
                            }
                            onRequestOpenOption(item)
                        }
                    )
            ) {
                DownloadCard(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        // Ctrl+click for multi-select handled via selection change
                        val newChecked = !isSelected
                        onItemSelectionChange(item.id, newChecked)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
