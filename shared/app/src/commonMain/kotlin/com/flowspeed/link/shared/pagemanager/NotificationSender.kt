package com.flowspeed.link.shared.pagemanager

import com.flowspeed.link.shared.ui.widget.MessageDialogType
import com.flowspeed.link.shared.ui.widget.NotificationType
import com.flowspeed.lib.util.compose.StringSource

interface NotificationSender {
    fun sendDialogNotification(title: StringSource, description: StringSource, type: MessageDialogType)
    fun sendNotification(tag: Any, title: StringSource, description: StringSource, type: NotificationType)
}
