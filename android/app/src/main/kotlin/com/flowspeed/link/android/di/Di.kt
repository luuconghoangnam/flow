package com.flowspeed.link.android.di

import com.flowspeed.link.android.FlowApp
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
 * Root DI container for the Android application.
 *
 * Module composition order matters — modules listed later can
 * depend on bindings from earlier modules.
 *
 * @see coroutineModule      — Application-wide CoroutineScope
 * @see serializationModule  — kotlinx.serialization Json config
 * @see storageModule        — DataStore settings & persistence
 * @see networkModule        — OkHttpClient, SSL, proxy
 * @see downloaderModule     — Download engine (DB, HTTP client, manager, queue)
 * @see downloadSystemModule — High-level download facade (categories, events)
 * @see updaterModule        — Auto-update checker & applier
 * @see platformModule       — OS services, version tracking, Android managers
 * @see uiModule             — Theme, language, icons, notifications
 */
fun getAppModule(context: FlowApp) = module {
    includes(
        coroutineModule,
        serializationModule,
        storageModule,
        networkModule,
        downloaderModule,
        downloadSystemModule,
        updaterModule,
        platformModule(context),
        uiModule,
    )
}

object Di : KoinComponent {
    /** Initializes the Koin DI container with all application modules. */
    fun boot(applicationContext: FlowApp) {
        startKoin {
            modules(getAppModule(applicationContext))
        }
    }
}
