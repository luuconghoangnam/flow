package com.flowspeed.link.desktop

import com.flowspeed.link.desktop.utils.IntegrationPortBroadcaster
import com.flowspeed.link.desktop.utils.singleInstance.Command
import com.flowspeed.link.desktop.utils.singleInstance.MutableSingleInstanceServerHandler
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
object Commands {
    val isReady = Command<Boolean>("isReady")
    val showUserThatAppIsRunning = Command<Unit>("showUserThatAppIsRunning")
    val getIntegrationPort = Command<Int>("getIntegrationPort")
    val exit = Command<Unit>("exit")
}
object SingleInstanceServerInitializer:KoinComponent {
    fun boot(mutableHandler: MutableSingleInstanceServerHandler){
        mutableHandler.add(Commands.showUserThatAppIsRunning){
            kotlin.runCatching { getAppComponent().openHome() }
        }
        mutableHandler.add(Commands.getIntegrationPort){
            IntegrationPortBroadcaster
                .getIntegrationPort().let { it?:-1 }
        }
        mutableHandler.add(Commands.isReady){
            // Ready as long as service is running (AppComponent may not exist in background mode)
            true
        }
        mutableHandler.add(Commands.exit) {
            runBlocking {
                getAppComponent().exitApp()
            }
        }
    }

    /** Lazily get AppComponent - only resolves when actually needed (e.g., user opens window). */
    private fun getAppComponent(): AppComponent {
        return org.koin.core.context.GlobalContext.get().get()
    }
}