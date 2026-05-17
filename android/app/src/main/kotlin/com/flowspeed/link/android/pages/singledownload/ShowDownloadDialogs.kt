package com.flowspeed.link.android.pages.singledownload

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.*
import com.flowspeed.link.android.ui.SheetHeader
import com.flowspeed.link.android.ui.SheetTitle
import com.flowspeed.link.android.ui.SheetUI
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.singledownloadpage.createStatusString
import com.flowspeed.link.shared.ui.widget.TransparentIconActionButton
import com.flowspeed.link.shared.util.OnFullyDismissed
import com.flowspeed.link.shared.util.ResponsiveDialog
import com.flowspeed.link.shared.util.rememberResponsiveDialogState
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.downloader.monitor.CompletedDownloadItemState
import com.flowspeed.lib.downloader.monitor.IDownloadItemState
import com.flowspeed.lib.downloader.monitor.ProcessingDownloadItemState
import com.flowspeed.lib.util.compose.asStringSource
import kotlinx.coroutines.delay

@Composable
private fun getDownloadTitle(itemState: IDownloadItemState): String {
    return buildString {
        if (itemState is ProcessingDownloadItemState && itemState.percent != null) {
            append("${itemState.percent}%")
            append(" ")
        }
        append(createStatusString(itemState).rememberString())
    }
}


@Composable
fun ShowDownloadDialog(
    singleDownloadComponent: AndroidSingleDownloadComponent,
    onRequestShowInDownloads: () -> Unit,
) {
    val itemState by singleDownloadComponent.itemStateFlow.collectAsState()
    val dialogState = rememberResponsiveDialogState(false)
    dialogState.OnFullyDismissed {
        singleDownloadComponent.close()
    }
    LaunchedEffect(Unit) {
        // animate open after activity becomes fully open
        // is there a better way?
        delay(10)
        dialogState.show()
    }
    val closeDialog = dialogState::hide
    ResponsiveDialog(
        dialogState, closeDialog
    ) {
        itemState?.let { downloadItemState ->
            SheetUI(header = {
                SheetHeader(
                    headerTitle = {
                        SheetTitle(getDownloadTitle(downloadItemState))
                    },
                    headerActions = {
                        if (singleDownloadComponent.comesFromExternalApplication) {
                            TransparentIconActionButton(
                                MyIcons.externalLink,
                                contentDescription = Res.string.show_downloads.asStringSource(),
                                onClick = onRequestShowInDownloads,
                            )
                        }
                        TransparentIconActionButton(
                            MyIcons.close,
                            contentDescription = Res.string.close.asStringSource(),
                            onClick = closeDialog
                        )
                    }
                )
            }) {
                AnimatedContent(
                    targetState = downloadItemState,
                    contentKey = {
                        when (it) {
                            is CompletedDownloadItemState -> 0
                            is ProcessingDownloadItemState -> 1
                        }
                    }
                ) { downloadItemState ->
                    when (downloadItemState) {
                        is CompletedDownloadItemState -> {
                            CompletedDownloadPage(
                                singleDownloadComponent,
                                downloadItemState,
                            )
                        }

                        is ProcessingDownloadItemState -> {
                            ProgressDownloadPage(
                                singleDownloadComponent,
                                downloadItemState,
                            )
                        }
                    }
                }

            }
        }
    }

}



