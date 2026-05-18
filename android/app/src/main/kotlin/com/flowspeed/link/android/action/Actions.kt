package com.flowspeed.link.android.action

import com.flowspeed.link.android.util.pagemanager.IBrowserPageManager
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.lib.util.compose.action.AnAction
import com.flowspeed.lib.util.compose.action.simpleAction
import com.flowspeed.lib.util.compose.asStringSource

fun createOpenBrowserAction(
    browserPageManager: IBrowserPageManager,
): AnAction {
    return simpleAction(
        Res.string.browser.asStringSource(),
        MyIcons.earth,
    ) {
        browserPageManager.openBrowser(null)
    }
}
