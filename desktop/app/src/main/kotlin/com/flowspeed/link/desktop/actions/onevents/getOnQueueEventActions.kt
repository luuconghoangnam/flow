package com.flowspeed.link.desktop.actions.onevents

import com.flowspeed.link.desktop.PowerActionManager
import com.flowspeed.lib.util.desktop.poweraction.PowerActionConfig
import com.flowspeed.link.desktop.pages.poweractionalert.PowerActionComponent
import com.flowspeed.link.desktop.storage.DesktopExtraQueueSettings
import com.flowspeed.link.shared.storage.IExtraQueueSettingsStorage
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueEventAction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class DesktopOnQueueEventActionProvider(
    private val desktopExtraQueueSettingsStorage: IExtraQueueSettingsStorage<DesktopExtraQueueSettings>,
) : OnQueueCompletionActionProvider, KoinComponent {
    // TODO: BUG
    // at the moment if I move this to constructor the DI halts
    // probably due to Circular Dependency but no exception is thrown
    // I need to redesign the dependency graph to prevent these sorts of issues!
    private val powerActionManager: PowerActionManager by inject()

    override suspend fun getOnQueueEventActions(queueId: Long): List<OnQueueEventAction> {
        return desktopExtraQueueSettingsStorage.getExtraQueueSettings(queueId).let {
            buildList {
                it.getPowerActionConfigOnFinish()?.let { powerAction ->
                    add(
                        PowerActionOnQueueFinishOrTimeEnd(
                            powerActionManager,
                            powerAction,
                        )
                    )
                }
            }
        }
    }
}

class PowerActionOnQueueFinishOrTimeEnd(
    private val powerActionManager: PowerActionManager,
    private val powerActionConfig: PowerActionConfig,
) : OnQueueEventAction {
    override suspend fun onQueueCompleted(queueId: Long) {
        powerActionManager.initiatePowerAction(
            powerActionConfig,
            PowerActionComponent.PowerActionReason.QueueWorkFinished
        )
    }

    override suspend fun onQueueEndTimeReached(queueId: Long) {
        powerActionManager.initiatePowerAction(
            powerActionConfig,
            PowerActionComponent.PowerActionReason.QueueEndTimeReached
        )
    }
}
