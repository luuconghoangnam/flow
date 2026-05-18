package com.flowspeed.link.desktop.ui.widget

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.window.custom.CustomWindow
import com.flowspeed.link.desktop.window.custom.WindowTitle
import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.flowspeed.link.shared.ui.widget.ActionButton
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.MessageDialogType
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.resources.myStringResource
import com.flowspeed.lib.util.desktop.screen.applyUiScale
import java.awt.Dimension
import java.util.UUID

data class MessageDialogModel(
    val id: String = UUID.randomUUID().toString(),
    val title: StringSource,
    val description: StringSource,
    val type: MessageDialogType = MessageDialogType.Info,
)

@Composable
fun ShowMessageDialogs(
    appComponent: AppComponent,
) {
    val list by appComponent.dialogMessages.collectAsState()

    for (msg in list) {
        MessageDialog(
            msgContent = msg,
            onConfirm = {
                appComponent.onDismissDialogMessage(msg)
            }
        )
    }
}

@Composable
fun MessageDialog(
    msgContent: MessageDialogModel,
    onConfirm: () -> Unit,
) {
    val uiScale = LocalUiScale.current
    val h = 200.applyUiScale(uiScale)
    val w = 400.applyUiScale(uiScale)
    val state = rememberWindowState(
        size = DpSize(w.dp, h.dp)
    )
    CustomWindow(
        state,
        onRequestMinimize = null,
        onRequestToggleMaximize = null,
        onCloseRequest = onConfirm,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(w, h)
        }
        val typeName = msgContent.type.toString()
        WindowTitle(typeName)
        Row(
            Modifier.padding(8.dp),
        ) {
            val color = when (msgContent.type) {
                MessageDialogType.Error -> myColors.info
                MessageDialogType.Info -> myColors.warning
                MessageDialogType.Success -> myColors.success
                MessageDialogType.Warning -> myColors.warning
            }
            MyIcon(
                icon = MyIcons.info,
                tint = color,
                modifier = Modifier
                    .padding(16.dp)
                    .requiredSize(36.dp),
                contentDescription = null,
            )
            Column {
                Text(
                    msgContent.title.rememberString(),
                    fontSize = myTextSizes.xl,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    msgContent.description.rememberString(),
                    fontSize = myTextSizes.base,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ActionButton(
                        myStringResource(Res.string.ok),
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}
