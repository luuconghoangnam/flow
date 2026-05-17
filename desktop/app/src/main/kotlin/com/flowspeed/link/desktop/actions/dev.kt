package com.flowspeed.link.desktop.actions

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.di.Di
import com.flowspeed.link.desktop.pages.poweractionalert.PowerActionComponent
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.ui.widget.MessageDialogType
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.action.createDummyExceptionAction
import com.flowspeed.link.shared.action.createDummyMessageAction
import com.flowspeed.lib.util.compose.action.AnAction
import com.flowspeed.lib.util.compose.action.MenuItem
import com.flowspeed.lib.util.compose.action.simpleAction
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.desktop.poweraction.PowerActionConfig
import org.koin.core.component.get

private val appComponent = Di.get<AppComponent>()
val dummyMessage = createDummyMessageAction(appComponent)
val dummyException = createDummyExceptionAction()
val shutdown = simpleAction(
    Res.string.shutdown_now.asStringSource(),
    MyIcons.exit,
) {
    appComponent.initiatePowerAction(
        PowerActionConfig(PowerActionConfig.Type.Shutdown, false),
        PowerActionComponent.PowerActionReason.Unknown
    )
}
