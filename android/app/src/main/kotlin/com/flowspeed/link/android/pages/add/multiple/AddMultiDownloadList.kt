package com.flowspeed.link.android.pages.add.multiple

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.pages.adddownload.multiple.NewMultiDownloadState
import com.flowspeed.link.shared.ui.widget.CheckBox
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.FileIconProvider
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.ui.LocalContentColor
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.mySpacings
import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.lib.util.ifThen

@Composable
fun AddMultiDownloadList(
    modifier: Modifier,
    component: AndroidAddMultiDownloadComponent,
    itePaddingValues: PaddingValues,
) {
    val dividerColor = myColors.onBackground / 0.5f
    val listState by component.filteredList.collectAsState()
    LazyColumn(modifier) {
        itemsIndexed(
            items = listState,
        ) { index, item ->
            val isSelected = remember(item, component.selectionList) {
                component.isSelected(item.id)
            }
            val isFirstItem = index == 0
            RenderAddDownloadItem(
                state = item,
                iconProvider = component.fileIconProvider,
                onSelectionChange = { selected ->
                    component.setSelect(item.id, selected)
                },
                onLongPress = {
                    component.openConfigurableList(
                        item.id
                    )
                },
                isSelected = isSelected,
                itemPadding = itePaddingValues,
                modifier = Modifier.ifThen(!isFirstItem) {
                    drawBehind {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    dividerColor,
                                    Color.Transparent,
                                )
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, 0f)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun RenderAddDownloadItem(
    state: NewMultiDownloadState,
    iconProvider: FileIconProvider,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    itemPadding: PaddingValues,
    modifier: Modifier,
) {
    val name = state.name
    val icon = iconProvider.rememberIcon(name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(mySpacings.thumbSize)
            .ifThen(isSelected) {
                background(
                    myColors.selectionGradient(1f, 0.5f)
                )
            }
            .combinedClickable(
                onClick = {
                    onSelectionChange(!isSelected)
                },
                onLongClick = {
                    onLongPress()
                }
            )
            .padding(itemPadding)

    ) {
        Column {
            Text(
                text = state.link,
                color = LocalContentColor.current / 0.75f,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(mySpacings.mediumSpace))
            Text(
                text = name.takeIf { it.isNotEmpty() } ?: "...",
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(mySpacings.mediumSpace))
            val sizeTitle = myStringResource(Res.string.size)
            val sizeValue = state.sizeString.rememberString()
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(
                    value = isSelected,
                    onValueChange = {
                        onSelectionChange(it)
                    },
                    size = 24.dp,
                )
                Spacer(Modifier.width(mySpacings.largeSpace))
                MyIcon(
                    icon = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(0.75f)
                )
                Spacer(Modifier.width(mySpacings.largeSpace))
                Text(
                    text = "$sizeTitle: $sizeValue",
                    maxLines = 1,
                )
            }
        }

    }
}
