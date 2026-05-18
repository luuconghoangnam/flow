package com.flowspeed.link.desktop.bootstrap

import com.flowspeed.link.desktop.AppArguments
import com.flowspeed.link.desktop.Commands
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.desktop.utils.IntegrationPortBroadcaster
import com.flowspeed.link.desktop.utils.singleInstance.SingleInstanceUtil
import kotlin.system.exitProcess

/**
 * Handles CLI dispatch commands that communicate with an already-running instance
 * and exit immediately (e.g., --version, --exit, --get-integration-port).
 *
 * Also handles the --start-if-not-started flow that spawns a background process
 * and waits for it to become ready.
 */
object SingleInstanceLauncher {

    /** Prints the app version to stdout and exits. */
    fun dispatchVersionAndExit(): Nothing {
        print(AppInfo.version)
        exitProcess(0)
    }

    /** Sends an exit command to the running instance and exits this process. */
    fun exitExistingProcessAndExit(singleInstance: SingleInstanceUtil): Nothing {
        singleInstance.sendToInstance(Commands.exit)
        exitProcess(0)
    }

    /** Queries the running instance for its integration port and exits. */
    fun dispatchIntegrationPortAndExit(singleInstance: SingleInstanceUtil): Nothing {
        val port = singleInstance.sendToInstance(Commands.getIntegrationPort)
            .orElse { IntegrationPortBroadcaster.INTEGRATION_UNKNOWN }
        print(port)
        exitProcess(0)
    }

    /**
     * Ensures an instance is running. If not, spawns one in background
     * and polls until it reports ready (or timeout).
     */
    fun startAndWaitForRunIfNotRunning(
        singleInstance: SingleInstanceUtil,
        howMuchWait: Long = 10_000,
        initialDelay: Long = 0,
        eachTimeDelay: Long = 500L,
    ) {
        val deadline = System.currentTimeMillis() + howMuchWait
        if (initialDelay > 0) Thread.sleep(initialDelay)

        var firstLoop = true
        while (true) {
            val isReady = singleInstance
                .sendToInstance(Commands.isReady)
                .orElse { false }

            if (isReady) return

            if (firstLoop) {
                startAppInAnotherProcess()
                firstLoop = false
            }
            if (System.currentTimeMillis() >= deadline) {
                exitProcess(1)
            }
            Thread.sleep(eachTimeDelay)
        }
    }

    @Suppress("DEPRECATION")
    private fun startAppInAnotherProcess() {
        val exeFile = requireNotNull(AppInfo.exeFile)
        val cmd = listOf(exeFile, AppArguments.Args.BACKGROUND).joinToString(" ")
        Runtime.getRuntime().exec(cmd)
    }
}
