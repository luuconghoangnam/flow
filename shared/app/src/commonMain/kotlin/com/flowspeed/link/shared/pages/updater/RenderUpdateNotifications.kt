package com.flowspeed.link.shared.pages.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.NotificationType
import com.flowspeed.link.shared.ui.widget.ShowNotification
import com.flowspeed.link.shared.util.mvi.HandleEffects
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.StringSource.*
import com.flowspeed.lib.util.compose.asStringSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RenderUpdateNotifications(updateComponent: UpdateComponent) {
    var message by remember { mutableStateOf(null as StringSource?) }
    var notificationType by remember { mutableStateOf(null as NotificationType?) }
    val scope = rememberCoroutineScope()
    var clearMessageInJob by remember {
        mutableStateOf(null as Job?)
    }

    fun clearMessageAfter(delay: Long) {
        clearMessageInJob?.cancel()
        clearMessageInJob = scope.launch {
            delay(delay)
            message = null
        }
    }
    HandleEffects(updateComponent) {
        when (it) {
            UpdateComponent.Effects.CheckingForUpdate -> {
                message = Res.string.update_checking_for_update.asStringSource()
                notificationType = NotificationType.Loading(null)
            }

            is UpdateComponent.Effects.Error -> {
                clearMessageAfter(3000)
                message = CombinedStringSource(
                    listOf(
                        Res.string.update_check_error.asStringSource(),
                        it.throwable.localizedMessage.orEmpty().asStringSource(),
                    ),
                    "\n",
                )
                it.throwable.printStackTrace()
                notificationType = NotificationType.Error
            }

            UpdateComponent.Effects.NoUpdate -> {
                clearMessageAfter(3000)
                message = Res.string.update_no_update.asStringSource()
                notificationType = NotificationType.Info
            }

            UpdateComponent.Effects.NewUpdate -> {
                message = null
                notificationType = null
            }
        }
    }

    message?.let { message ->
        ShowNotification(
            title = Res.string.update_updater.asStringSource(),
            description = message,
            type = notificationType ?: NotificationType.Info,
            tag = "Updater"
        )
    }
}
