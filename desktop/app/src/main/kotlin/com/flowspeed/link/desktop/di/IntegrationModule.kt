package com.flowspeed.link.desktop.di

import com.flowspeed.link.desktop.integration.IntegrationHandlerImp
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.desktop.utils.isInDebugMode
import com.flowspeed.link.integration.Integration
import com.flowspeed.link.integration.IntegrationHandler
import org.koin.dsl.module

/**
 * Browser integration DI module.
 *
 * Provides: Integration HTTP server and its request handler
 * for communication with the browser extension.
 */
val integrationModule = module {
    single<IntegrationHandler> { IntegrationHandlerImp() }
    single { Integration(get(), get(), get(), AppInfo.isInDebugMode()) }
}
