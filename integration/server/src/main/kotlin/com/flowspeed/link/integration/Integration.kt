/*
 * Copyright (c) 2025 Luu Cong Hoang Nam
 * Licensed under the Apache License, Version 2.0
 * https://github.com/luuconghoangnam/flowspeed.link
 */
package com.flowspeed.link.integration

import com.flowspeed.link.integration.http4k.MyHttp4KServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

sealed interface IntegrationResult {
    data object Inactive : IntegrationResult
    data class Fail(val throwable: Throwable) : IntegrationResult
    data class Success(val port: Int) : IntegrationResult
}

/**
 * Origin schemes used by browser extensions when making cross-origin requests
 * from their background/service-worker context. Regular web pages can only ever
 * have an http:// or https:// Origin — they cannot spoof these schemes, and the
 * browser itself sets the `Origin` header (it is a "forbidden header name" that
 * JavaScript cannot override). This lets us reject requests coming from an
 * arbitrary website while still accepting requests from the Flow browser extension,
 * with zero configuration required on the extension side.
 */
private val ALLOWED_EXTENSION_ORIGIN_SCHEMES = listOf(
    "chrome-extension://",
    "moz-extension://",
    "edge-extension://",
)

/**
 * Optional allowlist of specific extension IDs (e.g. "chrome-extension://abcdefgh...").
 * Empty means "accept any extension of an allowed scheme" — still blocks regular
 * websites entirely. Fill this in once the Chrome/Edge Web Store listing ID is known
 * to narrow the allowlist to exactly the official Flow extension.
 */
private val ALLOWED_EXTENSION_ORIGINS: Set<String> = emptySet()

private fun isFromTrustedExtension(request: MyRequest): Boolean {
    val origin = request.header("Origin") ?: return false
    val matchesScheme = ALLOWED_EXTENSION_ORIGIN_SCHEMES.any { origin.startsWith(it) }
    if (!matchesScheme) return false
    if (ALLOWED_EXTENSION_ORIGINS.isEmpty()) return true
    return origin in ALLOWED_EXTENSION_ORIGINS
}

/**
 * Wraps a handler for state-changing/sensitive endpoints so that only requests
 * originating from the Flow browser extension are accepted. Requests from regular
 * websites (which could otherwise forge a POST to localhost via fetch/XHR — a classic
 * "localhost CSRF" attack) are rejected with 403 before the handler body runs.
 */
private fun protectExtensionOnly(handler: Handler): Handler = { request ->
    if (isFromTrustedExtension(request)) {
        handler(request)
    } else {
        MyResponse.BadRequest(
            errorText = "Forbidden: request must originate from the Flow browser extension",
            statusCode = 403,
        )
    }
}

class Integration(
    val integrationHandler: IntegrationHandler,
    val scope: CoroutineScope,
    private val json: Json,
    val debugMode: Boolean,
) {

    private val portFlow = MutableStateFlow<Int?>(null)
    val integrationStatus = MutableStateFlow<IntegrationResult>(IntegrationResult.Inactive)

    fun enable(port: Int) {
        portFlow.update { port }
    }

    fun disable() {
        portFlow.update { null }
    }

    fun boot() {
        scope.launch {
            kotlin.runCatching {
                portFlow.collect { port ->
                    runCatching {
                        if (port != null) {
                            startServer(port)
                            integrationStatus.update { IntegrationResult.Success(port) }
                        } else {
                            stopServer()
                            integrationStatus.update { IntegrationResult.Inactive }
                        }
                    }.onFailure { throwable ->
                        integrationStatus.update {
                            IntegrationResult.Fail(throwable)
                        }
                        kotlin.runCatching {
                            disable()
                        }
                    }
                }
            }
        }
    }

    @Volatile
    private var server: MyServer? = null
    private suspend fun startServer(port: Int) {
        stopServer()
        val server = createServer(port)
        this.server = server
        withContext(Dispatchers.IO) {
//            println("start server")
            server.startMyServer()
        }
    }

    private suspend fun stopServer() {
        server?.let {
//            println("stop server")
            withContext(Dispatchers.IO) {
                it.stopMyServer()
            }
        }
        server = null
    }


    private fun createServer(port: Int): MyServer {
        val handlers = HandlerMap().apply {
            post("/add", protectExtensionOnly { request ->
                runBlocking {
                    val itemsToAdd = kotlin.runCatching {
                        val message = request.getBody().orEmpty()
                        AddDownloadsFromIntegration.createFromRequest(
                            json = json,
                            jsonData = message
                        )
                    }
                    itemsToAdd.onFailure { it.printStackTrace() }
                    itemsToAdd.getOrThrow().let { newImportRequest ->
                        integrationHandler.addDownload(
                            newImportRequest.items,
                            newImportRequest.options,
                        )
                    }
                }
                MyResponse.Text("OK")
            })
            get("/queues", protectExtensionOnly {
                runBlocking {
                    val queues = integrationHandler.listQueues()
                    val jsonResponse = json.encodeToString(ListSerializer(ApiQueueModel.serializer()), queues)
                    MyResponse.Text(jsonResponse)
                }
            })
            post("/start-headless-download", protectExtensionOnly { request ->
                runBlocking {
                    val itemsToAdd = kotlin.runCatching {
                        val message = request.getBody().orEmpty()
                        json.decodeFromString<NewDownloadTask>(message)
                    }
                    itemsToAdd.onFailure { it.printStackTrace() }
                    integrationHandler.addDownloadTask(itemsToAdd.getOrThrow())
                }
                MyResponse.Text("OK")
            })
            // Health checks stay unauthenticated: used by the extension popup purely to detect
            // "is Flow running on this port at all" before any sensitive action is taken.
            // It leaks no data and performs no action, so it's safe to leave origin-unchecked.
            get("/") {
                MyResponse.Text("pong")
            }
            post("/ping") {
                MyResponse.Text("pong")
            }
        }
        return MyHttp4KServer(port, handlers, debugMode)
    }
}
