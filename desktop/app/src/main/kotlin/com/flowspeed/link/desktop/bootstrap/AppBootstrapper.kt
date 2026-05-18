/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.desktop.bootstrap

import com.flowspeed.link.desktop.AppArguments
import com.flowspeed.link.desktop.SingleInstanceServerInitializer
import com.flowspeed.link.desktop.di.Di
import com.flowspeed.link.desktop.repository.AppRepository
import com.flowspeed.link.desktop.ui.Ui
import com.flowspeed.link.desktop.utils.GlobalAppExceptionHandler
import com.flowspeed.link.desktop.utils.KeepAwakeManager
import com.flowspeed.link.desktop.utils.MemoryManager
import com.flowspeed.link.desktop.utils.renderapi.CustomRenderApi
import com.flowspeed.link.desktop.utils.singleInstance.MutableSingleInstanceServerHandler
import com.flowspeed.link.integration.Integration
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.appinfo.PreviousVersion
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Orchestrates the application boot sequence.
 *
 * Responsibilities:
 * - Initialize the DI container
 * - Boot all subsystems in the correct order
 * - Start the UI event loop
 *
 * Boot order matters:
 * 1. DI container (all dependencies become available)
 * 2. CustomRenderApi (rendering config before any UI)
 * 3. AppRepository (settings loaded for download engine)
 * 4. Integration server (ready to receive browser extension requests)
 * 5. DownloadSystem (download engine + queues)
 * 6. PreviousVersion (migration checks)
 * 7. KeepAwakeManager (prevent sleep during downloads)
 * 8. MemoryManager (idle memory reclamation)
 * 9. SingleInstanceServer (IPC commands)
 * 10. UI (Compose application loop — blocks until exit)
 */
class AppBootstrapper : AutoCloseable, KoinComponent {
    private val appRepository: AppRepository by inject()
    private val integration: Integration by inject()
    private val downloadSystem: DownloadSystem by inject()
    private val previousVersion: PreviousVersion by inject()
    private val keepAwakeManager: KeepAwakeManager by inject()
    private val memoryManager: MemoryManager by inject()
    private val customRenderApi: CustomRenderApi by inject()

    /**
     * Runs the full boot sequence and starts the UI.
     * This call blocks until the application exits.
     */
    fun start(
        appArguments: AppArguments,
        singleInstanceServerHandler: MutableSingleInstanceServerHandler,
        globalAppExceptionHandler: GlobalAppExceptionHandler,
    ) {
        try {
            runBlocking {
                Di.boot()
                bootSubsystems()
                SingleInstanceServerInitializer.boot(singleInstanceServerHandler)
                Ui.boot(appArguments, globalAppExceptionHandler)
            }
        } catch (e: Exception) {
            globalAppExceptionHandler.onProcessIsUseless()
            throw e
        }
    }

    private suspend fun bootSubsystems() {
        customRenderApi.boot()
        appRepository.boot()
        integration.boot()
        downloadSystem.boot()
        previousVersion.boot()
        keepAwakeManager.boot()
        memoryManager.boot()
    }

    override fun close() {
        // Reserved for future cleanup (e.g., stopping services gracefully)
    }
}
