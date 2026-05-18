/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.android

import android.app.Application
import com.flowspeed.link.android.di.Di
import com.flowspeed.link.android.pages.onboarding.permissions.PermissionManager
import com.flowspeed.link.android.util.FlowAppManager
import com.flowspeed.link.android.util.AndroidGlobalExceptionHandler
import com.flowspeed.link.android.util.AppInfo
import com.flowspeed.link.android.util.ApplicationBackgroundTracker
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.util.appinfo.PreviousVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FlowApp : Application(), KoinComponent {
    val TAG_NAME = FlowApp::class.simpleName!!
    val appManager: FlowAppManager by inject()
    val appRepository: BaseAppRepository by inject()
    val previousVersion: PreviousVersion by inject()
    val scope: CoroutineScope by inject()
    override fun onCreate() {
        super.onCreate()
        AppInfo.init(this)
        Di.boot(this)
        ApplicationBackgroundTracker.startTracking(this)
        appRepository.boot()
        previousVersion.boot()
        Thread.setDefaultUncaughtExceptionHandler(
            AndroidGlobalExceptionHandler(
                this,
                Thread.getDefaultUncaughtExceptionHandler(),
            )
        )
        appManager.boot()
    }
}
