package com.flowspeed.link.android.pages.browser.bookmark

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowspeed.link.android.storage.BrowserBookmark
import com.flowspeed.link.android.ui.SheetHeader
import com.flowspeed.link.android.ui.SheetTitle
import com.flowspeed.link.android.ui.SheetUI
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.ui.widget.TransparentIconActionButton
import com.flowspeed.link.shared.util.ResponsiveDialog
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.rememberResponsiveDialogState
import com.flowspeed.link.shared.util.ui.LocalContentColor
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.theme.mySpacings
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.resources.myStringResource

@Composable
fun BookmarkList(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    bookmarks: List<BrowserBookmark>,
    onRequestNewBookmark: () -> Unit,
    onRequestEditBookmark: (BrowserBookmark) -> Unit,
    onBookmarkClick: (BrowserBookmark) -> Unit,
    onRemoveBookmarkRequest: (BrowserBookmark) -> Unit,
) {
    val responsiveState = rememberResponsiveDialogState(visible)
    LaunchedEffect(visible) {
        if (visible) {
            responsiveState.show()
        } else {
            responsiveState.hide()
        }
    }
    ResponsiveDialog(
        state = responsiveState,
        onDismiss = onDismissRequest,
    ) {
        SheetUI(
            header = {
                SheetHeader(
                    headerTitle = {
                        SheetTitle(
                            myStringResource(Res.string.browser_bookmarks),
                        )
                    },
                    headerActions = {
                        TransparentIconActionButton(
                            MyIcons.add,
                            Res.string.add.asStringSource(),
                        ) {
                            onRequestNewBookmark()
                        }
                        TransparentIconActionButton(
                            MyIcons.close,
                            Res.string.close.asStringSource(),
                        ) {
                            onDismissRequest()
                        }
                    }
                )
            }
        ) {
            LazyColumn {
                items(bookmarks) { bookmark ->
                    Row(
                        modifier = Modifier
                            .heightIn(mySpacings.thumbSize)
                            .combinedClickable(
                                onLongClick = { onRequestEditBookmark(bookmark) },
                                onClick = { onBookmarkClick(bookmark) }
                            )
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val modifier = Modifier.size(24.dp)
                        MyIcon(
                            MyIcons.earth,
                            contentDescription = null,
                            modifier = modifier,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f),
                        ) {
                            Text(
                                text = bookmark.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = bookmark.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = myTextSizes.sm,
                                color = LocalContentColor.current / 0.75f
                            )
                        }

                        WithContentAlpha(0.5f) {
                            TransparentIconActionButton(
                                MyIcons.remove,
                                Res.string.remove.asStringSource(),
                            ) {
                                onRemoveBookmarkRequest(bookmark)
                            }
                        }
                    }
                }
            }
        }
    }
}
