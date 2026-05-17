package com.flowspeed.link.android.pages.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.flowspeed.link.android.ui.widget.WebView


@Composable
fun FlowWebView(
    modifier: Modifier = Modifier,
    webViewHolder: WebViewHolder,
) {
    val tab = webViewHolder.tab
    key(tab.tabId) {
        val wState = tab.tabState
        val navigator = webViewHolder.navigator
        WebView(
            state = wState,
            modifier = modifier,
            captureBackPresses = false,
            navigator = navigator,
            client = webViewHolder.client,
            chromeClient = webViewHolder.chromeClient,
            onDispose = {
                webViewHolder.deactivate()
            },
            factory = {
                webViewHolder.activate(it)
            },
        )
    }

}
