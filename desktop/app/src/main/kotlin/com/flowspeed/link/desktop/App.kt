/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.desktop

import com.flowspeed.link.desktop.bootstrap.AppBootstrapper
import com.flowspeed.link.desktop.bootstrap.SingleInstanceLauncher
import com.flowspeed.link.desktop.utils.*
import com.flowspeed.link.desktop.utils.singleInstance.AnotherInstanceIsRunning
import com.flowspeed.link.desktop.utils.singleInstance.MutableSingleInstanceServerHandler
import com.flowspeed.link.desktop.utils.singleInstance.SingleInstanceUtil
import com.flowspeed.link.shared.util.AppVersion
import kotlin.system.exitProcess

/**
 * Application entry point.
 *
 * Handles CLI argument dispatch, single-instance locking,
 * and delegates to [AppBootstrapper] for the actual boot sequence.
 */
fun main(args: Array<String>) {
    try {
        AppArguments.init(args)
        AppProperties.boot()

        val appArguments = AppArguments.get()
        val singleInstance = SingleInstanceUtil(AppInfo.definedPaths.configDir)

        // CLI dispatch commands — these print output and exit immediately
        if (appArguments.version) {
            SingleInstanceLauncher.dispatchVersionAndExit()
        }
        if (appArguments.exit) {
            SingleInstanceLauncher.exitExistingProcessAndExit(singleInstance)
        }
        if (appArguments.startIfNotStarted && !AppInfo.isInIDE()) {
            SingleInstanceLauncher.startAndWaitForRunIfNotRunning(singleInstance)
        }
        if (appArguments.getIntegrationPort) {
            SingleInstanceLauncher.dispatchIntegrationPortAndExit(singleInstance)
        }

        // Normal startup — acquire single-instance lock and boot
        startApplication(singleInstance, appArguments)

    } catch (e: Throwable) {
        System.err.println("Failed to start ${AppInfo.displayName}:")
        e.printStackTrace()
        exitProcess(-1)
    }
}

/**
 * Acquires the single-instance lock and starts the application.
 * If another instance is already running, notifies it and returns.
 */
private fun startApplication(
    singleInstance: SingleInstanceUtil,
    appArguments: AppArguments,
) {
    val singleInstanceServerHandler by lazy { MutableSingleInstanceServerHandler() }

    try {
        singleInstance.lockInstance { singleInstanceServerHandler }
    } catch (_: AnotherInstanceIsRunning) {
        println("instance already running")
        singleInstance.sendToInstance(Commands.showUserThatAppIsRunning)
        return
    }

    if (AppInfo.isInIDE()) {
        println("app version ${AppVersion.get()} started (IDE mode)")
    }

    val globalExceptionHandler = createAndSetGlobalExceptionHandler()
    AppBootstrapper().use {
        it.start(
            appArguments = appArguments,
            globalAppExceptionHandler = globalExceptionHandler,
            singleInstanceServerHandler = singleInstanceServerHandler,
        )
    }
}
