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
    // Injected lazily to avoid circular dependency in the Koin DI graph.
    // PowerActionManager depends on components that also depend on this provider,
    // so constructor injection would cause a cycle. Lazy field injection breaks the cycle.
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
