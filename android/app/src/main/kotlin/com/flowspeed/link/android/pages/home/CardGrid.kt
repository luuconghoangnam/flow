package com.flowspeed.link.android.pages.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.DownloadCard
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.util.compose.resources.myStringResource

/**
 * Android card grid layout for downloads.
 * Uses 1 column for narrow screens (<600dp) and 2 columns for wide screens.
 * Supports tap to select and long-press for context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardGrid(
    downloadList: List<IDownloadItemState>,
    selectionList: List<Long>,
    onItemSelectionChange: (Long, Boolean) -> Unit,
    onNewSelection: (List<Long>) -> Unit,
    onItemClicked: (IDownloadItemState) -> Unit,
    onItemLongClicked: (IDownloadItemState) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val columns = if (screenWidthDp > 600) GridCells.Fixed(2) else GridCells.Fixed(1)
    BackHandler(selectionList.isNotEmpty()) {
        onNewSelection(emptyList())
    }

    if (downloadList.isEmpty()) {
        Box(modifier.fillMaxSize()) {
            WithContentAlpha(0.75f) {
                Text(
                    myStringResource(Res.string.list_is_empty),
                    Modifier.align(Alignment.Center),
                    maxLines = 1,
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
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
                    .combinedClickable(
                        onClick = {
                            if (selectionList.isNotEmpty()) {
                                onItemSelectionChange(item.id, !isSelected)
                            } else {
                                onItemClicked(item)
                            }
                        },
                        onLongClick = {
                            onItemLongClicked(item)
                        },
                    )
            ) {
                DownloadCard(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        if (selectionList.isNotEmpty()) {
                            onItemSelectionChange(item.id, !isSelected)
                        } else {
                            onItemClicked(item)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}
