package com.flowspeed.link.android.pages.home

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.IconActionButton
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.ui.widget.TransparentIconActionButton
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.util.compose.asStringSource

@Immutable
data class QueueSelectedItemsMenuProps(
    val queueName: String,
    val onRequestQueueItemsUp: () -> Unit,
    val onRequestQueueItemsDown: () -> Unit,
    val onRequestRemoveItemsFromQueue: () -> Unit,
)

@Composable
fun RenderSelectedQueueItemsOption(
    props: QueueSelectedItemsMenuProps,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransparentIconActionButton(
            icon = MyIcons.up,
            contentDescription = Res.string.move_up.asStringSource(),
            onClick = props.onRequestQueueItemsUp,
            shape = RectangleShape,
        )
        TransparentIconActionButton(
            icon = MyIcons.down,
            contentDescription = Res.string.move_down.asStringSource(),
            onClick = props.onRequestQueueItemsDown,
            shape = RectangleShape,
        )
        Text(
            props.queueName,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        TransparentIconActionButton(
            MyIcons.minus,
            Res.string.remove.asStringSource(),
            onClick = props.onRequestRemoveItemsFromQueue,
            shape = RectangleShape,
        )
    }
}
