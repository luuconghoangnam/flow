package com.flowspeed.link.desktop.delegate

import com.flowspeed.lib.downloader.DownloadManagerEvents
import com.flowspeed.lib.downloader.downloaditem.contexts.ResumedBy
import com.flowspeed.lib.downloader.downloaditem.contexts.User
import com.flowspeed.lib.downloader.exception.TooManyErrorException
import com.flowspeed.lib.downloader.utils.ExceptionUtils
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.combineStringSources
import com.flowspeed.link.desktop.AppEffects
import com.flowspeed.link.desktop.storage.AppSettingsStorage
import com.flowspeed.link.desktop.ui.widget.MessageDialogModel
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.pagemanager.DownloadDialogManager
import com.flowspeed.link.shared.pagemanager.NotificationSender
import com.flowspeed.link.shared.ui.widget.MessageDialogType
import com.flowspeed.link.shared.ui.widget.NotificationModel
import com.flowspeed.link.shared.ui.widget.NotificationType
import com.flowspeed.link.shared.util.mvi.ContainsEffects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.awt.Toolkit

/**
 * Handles all notification and dialog message logic.
 *
 * Responsibilities:
 * - System beep on notifications
 * - Toast-style notifications via effects
 * - Modal dialog messages
 * - Download event → notification mapping
 */
class NotificationDelegate(
    private val appSettings: AppSettingsStorage,
    private val effectSender: ContainsEffects<AppEffects>,
    private val downloadDialogManager: DownloadDialogManager,
) : NotificationSender {

    val dialogMessages: MutableStateFlow<List<MessageDialogModel>> = MutableStateFlow(emptyList())

    override fun sendNotification(
        tag: Any,
        title: StringSource,
        description: StringSource,
        type: NotificationType,
    ) {
        beep()
        effectSender.sendEffect(
            AppEffects.SimpleNotificationNotification(
                NotificationModel(
                    tag = tag,
                    initialTitle = title,
                    initialDescription = description,
                    initialNotificationType = type
                )
            )
        )
    }

    override fun sendDialogNotification(
        title: StringSource,
        description: StringSource,
        type: MessageDialogType,
    ) {
        beep()
        newDialogMessage(MessageDialogModel(title = title, description = description, type = type))
    }

    fun onDismissDialogMessage(msgDialogModel: MessageDialogModel) {
        dialogMessages.update { it.filter { item -> msgDialogModel.id != item.id } }
    }

    /**
     * Maps download manager events to user-facing notifications.
     * Only notifies for user-initiated downloads (not queue-triggered).
     */
    fun onNewDownloadEvent(event: DownloadManagerEvents) {
        if (event.context[ResumedBy]?.by !is User) return

        when (event) {
            is DownloadManagerEvents.OnJobCanceled -> handleJobCanceled(event)
            is DownloadManagerEvents.OnJobCompleted -> handleJobCompleted(event)
            is DownloadManagerEvents.OnJobStarting -> handleJobStarting(event)
            else -> {}
        }
    }

    private fun handleJobCanceled(event: DownloadManagerEvents.OnJobCanceled) {
        val exception = event.e
        if (ExceptionUtils.isNormalCancellation(exception)) return

        var isMaxTryReachedError = false
        val actualCause = if (exception is TooManyErrorException) {
            isMaxTryReachedError = true
            exception.findActualDownloadErrorCause()
        } else exception

        if (ExceptionUtils.isNormalCancellation(actualCause)) return

        val prefix = if (isMaxTryReachedError) "Too Many Error: " else "Error: "
        val reason = actualCause.message?.asStringSource() ?: Res.string.unknown.asStringSource()

        sendNotification(
            tag = "downloadId=${event.downloadItem.id}",
            title = event.downloadItem.name.asStringSource(),
            description = listOf(prefix.asStringSource(), reason).combineStringSources(),
            type = NotificationType.Error,
        )
    }

    private fun handleJobCompleted(event: DownloadManagerEvents.OnJobCompleted) {
        sendNotification(
            tag = "downloadId=${event.downloadItem.id}",
            title = event.downloadItem.name.asStringSource(),
            description = Res.string.finished.asStringSource(),
            type = NotificationType.Success,
        )
        if (appSettings.showDownloadCompletionDialog.value) {
            downloadDialogManager.openDownloadDialog(event.downloadItem.id)
        }
    }

    private fun handleJobStarting(event: DownloadManagerEvents.OnJobStarting) {
        if (appSettings.showDownloadProgressDialog.value) {
            downloadDialogManager.openDownloadDialog(event.downloadItem.id)
        }
    }

    private fun beep() {
        if (appSettings.notificationSound.value) {
            Toolkit.getDefaultToolkit().beep()
        }
    }

    private fun newDialogMessage(msgDialogModel: MessageDialogModel) {
        dialogMessages.update {
            it.filter { item -> item.id != msgDialogModel.id }.plus(msgDialogModel)
        }
    }
}
