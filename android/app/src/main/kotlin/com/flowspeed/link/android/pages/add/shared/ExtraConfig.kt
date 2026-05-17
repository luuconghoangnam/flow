package com.flowspeed.link.android.pages.add.shared

import com.flowspeed.link.shared.ui.configurable.RenderConfigurable
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.div
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.flowspeed.link.android.ui.SheetHeader
import com.flowspeed.link.android.ui.SheetTitle
import com.flowspeed.link.android.ui.SheetUI
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.link.shared.ui.configurable.ConfigurableUiProps
import com.flowspeed.link.shared.util.OnFullyDismissed
import com.flowspeed.link.shared.util.ResponsiveDialog
import com.flowspeed.link.shared.util.rememberResponsiveDialogState
import com.flowspeed.link.shared.util.ui.MultiplatformVerticalScrollbar
import com.flowspeed.link.shared.util.ui.theme.LocalUiScale
import com.flowspeed.link.shared.util.ui.theme.myShapes
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter

@Composable
fun ExtraConfig(
    isOpened: Boolean,
    onDismiss: () -> Unit,
    configurables: List<Configurable<*>>,
) {
    val dialogState = rememberResponsiveDialogState(false)
    LaunchedEffect(isOpened) {
        if (isOpened) {
            dialogState.show()
        } else {
            dialogState.hide()
        }
    }
    dialogState.OnFullyDismissed {
        onDismiss()
    }
    ResponsiveDialog(
        state = dialogState,
        onDismiss = {
            dialogState.hide()
        }
    ) {
        SheetUI(
            header = {
                SheetHeader(
                    headerTitle = {
                        SheetTitle("Extra Config")
                    },
                    headerActions = {}
                )
            }
        ) {
            Box {
                val scrollState = rememberScrollState()
                Column(
                    Modifier.verticalScroll(scrollState)
                ) {
                    for ((index, cfg) in configurables.withIndex()) {
                        RenderConfigurable(
                            cfg,
                            ConfigurableUiProps(
                                itemPaddingValues = PaddingValues(vertical = 8.dp, horizontal = 32.dp)
                            )
                        )
                        if (index != configurables.lastIndex) {
                            Divider()
                        }
                    }
                }
                MultiplatformVerticalScrollbar(
                    rememberScrollbarAdapter(scrollState),
                    Modifier
                        .matchParentSize()
                        .wrapContentWidth()
                        .align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(myColors.onBackground / 10),
    )
}
