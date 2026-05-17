package com.flowspeed.link.android.service

import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.core.app.ServiceCompat
import com.flowspeed.link.android.util.FlowServiceNotificationManager
import com.flowspeed.link.android.util.AndroidConstants
import com.flowspeed.link.android.util.AndroidUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadSystemService : Service(), KoinComponent {
    val FlowServiceNotificationManager: FlowServiceNotificationManager by inject()
    override fun onCreate() {
        _isServiceRunningFlow.value = true
        AndroidUi.boot()
        FlowServiceNotificationManager.initNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("DownloadSystemService", "onStartCommand: at the beginning")
        startForeground(
            AndroidConstants.SERVICE_NOTIFICATION_ID,
            FlowServiceNotificationManager.createMainNotification()
        )
        FlowServiceNotificationManager.startUpdatingNotifications()
        Log.i("DownloadSystemService", "onStartCommand: service goes to foreground")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        FlowServiceNotificationManager.stopUpdatingNotifications()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        _isServiceRunningFlow.value = false
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private val _isServiceRunningFlow = MutableStateFlow(false)
        val isServiceRunningFlow = _isServiceRunningFlow.asStateFlow()
        fun isServiceRunning(): Boolean {
            return isServiceRunningFlow.value
        }

        suspend fun awaitStart() {
            if (isServiceRunning()) {
                return
            }
            isServiceRunningFlow.first { it }
        }
    }
}
