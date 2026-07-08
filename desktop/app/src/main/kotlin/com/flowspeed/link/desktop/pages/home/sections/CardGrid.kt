package com.flowspeed.link.desktop.pages.home.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.onClick
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.DownloadCard
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.DOUBLE_CLICK_DELAY
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.lib.util.desktop.isCtrlPressed
import kotlinx.coroutines.delay

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
    onRequestOpenDownload: (Long) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val windowInfo = LocalWindowInfo.current
    fun changeAllSelection(isSelected: Boolean) {
        onNewSelection(if (isSelected) downloadList.map { it.id } else emptyList())
    }

    if (downloadList.isEmpty()) {
        Box(modifier.fillMaxSize()) {
            WithContentAlpha(0.75f) {
                Text(myStringResource(Res.string.list_is_empty), Modifier.align(Alignment.Center))
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent {
                when {
                    it.key == Key.A && isCtrlPressed(windowInfo) -> {
                        changeAllSelection(true)
                        true
                    }

                    it.key == Key.Escape -> {
                        changeAllSelection(false)
                        true
                    }

                    else -> false
                }
            }
            .clickable {
                changeAllSelection(false)
            },
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = downloadList,
            key = { it.id },
        ) { item ->
            val isSelected = selectionList.contains(item.id)
            var shouldWaitForSecondClick by remember { mutableStateOf(false) }
            LaunchedEffect(shouldWaitForSecondClick) {
                delay(DOUBLE_CLICK_DELAY)
                if (shouldWaitForSecondClick) {
                    shouldWaitForSecondClick = false
                }
            }
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
                        if (shouldWaitForSecondClick) {
                            onRequestOpenDownload(item.id)
                            shouldWaitForSecondClick = false
                        } else if (isCtrlPressed(windowInfo)) {
                            onItemSelectionChange(item.id, !isSelected)
                        } else {
                            changeAllSelection(false)
                            onItemSelectionChange(item.id, true)
                            shouldWaitForSecondClick = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
