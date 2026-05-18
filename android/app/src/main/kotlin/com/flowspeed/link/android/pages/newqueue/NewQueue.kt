package com.flowspeed.link.android.pages.newqueue

import androidx.compose.runtime.Composable
import com.flowspeed.link.android.ui.configurable.SheetInput
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.MyTextField
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.resources.myStringResource

@Composable
fun NewQueueSheet(
    onQueueCreate: (String) -> Unit,
    isOpened: Boolean,
    onCloseRequest: () -> Unit,
) {
    SheetInput(
        title = Res.string.add_new_queue.asStringSource(),
        validate = { it.isNotEmpty() },
        isOpened = isOpened,
        initialValue = { "" },
        onDismiss = onCloseRequest,
        onConfirm = onQueueCreate,
        inputContent = {
            MyTextField(
                modifier = it.modifier,
                text = it.editingValue,
                onTextChange = it.setEditingValue,
                placeholder = myStringResource(Res.string.queue_name),
            )
        },
    )
}
