/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.service

import com.flowspeed.link.service.lifecycle.ServiceLifecycle
import com.flowspeed.link.service.lifecycle.SingleInstanceLock
import kotlin.system.exitProcess

/**
 * Entry point for the Flow background service.
 *
 * This process runs headless (no UI) and handles:
 * - Browser extension HTTP requests (download interception)
 * - Download engine (multi-part, resume, queue management)
 * - IPC server for UI process communication
 *
 * Designed to be compiled with GraalVM native-image for ~20-30MB idle RAM.
 */
fun main(args: Array<String>) {
    val config = ServiceConfig.parse(args)

    val lock = SingleInstanceLock(config.dataDir)

    if (!lock.tryAcquire()) {
        // Another instance is running — forward command and exit
        val port = lock.getRunningInstancePort()
        if (port != null) {
            println("Service already running on IPC port $port")
        } else {
            System.err.println("Failed to acquire lock and no running instance found")
        }
        exitProcess(0)
    }

    // Register shutdown hook for graceful cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        lock.release()
    })

    try {
        val lifecycle = ServiceLifecycle(config, lock)
        lifecycle.boot()
        // Block main thread — service runs until shutdown signal
        lifecycle.awaitShutdown()
    } catch (e: Exception) {
        System.err.println("Service failed to start: ${e.message}")
        e.printStackTrace()
        lock.release()
        exitProcess(1)
    }
}
