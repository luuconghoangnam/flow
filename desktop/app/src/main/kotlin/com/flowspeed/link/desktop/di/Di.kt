/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.desktop.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Application-wide coroutine scope. */
val coroutineModule = module {
    single { CoroutineScope(SupervisorJob()) }
}

/**
 * Root DI container for the desktop application.
 *
 * Module composition order matters — modules listed later can
 * depend on bindings from earlier modules.
 *
 * @see downloaderModule    — Download engine (DB, HTTP client, manager, queue)
 * @see downloadSystemModule — High-level download facade (categories, events)
 * @see serializationModule — kotlinx.serialization Json config
 * @see networkModule       — OkHttpClient, SSL, proxy
 * @see integrationModule   — Browser extension HTTP server
 * @see updaterModule       — Auto-update checker & applier
 * @see storageModule       — DataStore settings & persistence
 * @see platformModule      — OS services (startup, keep-awake, memory, native messaging)
 * @see uiModule            — AppComponent, theme, fonts, language, notifications
 */
val appModule = module {
    includes(
        coroutineModule,
        serializationModule,
        networkModule,
        downloaderModule,
        downloadSystemModule,
        integrationModule,
        updaterModule,
        storageModule,
        platformModule,
        uiModule,
    )
}

object Di : KoinComponent {
    /** Initializes the Koin DI container with all application modules. */
    fun boot() {
        startKoin {
            modules(appModule)
        }
    }
}
