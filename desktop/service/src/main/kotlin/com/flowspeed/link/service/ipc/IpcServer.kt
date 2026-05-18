package com.flowspeed.link.service.ipc

import com.flowspeed.link.service.ServiceDi
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * IPC HTTP server for UI process communication.
 *
 * Runs on localhost only (not accessible from network).
 * Provides REST-like API for download management, queue control,
 * configuration, and real-time event streaming (SSE).
 */
class IpcServer(
    port: Int,
    private val di: ServiceDi,
    private val json: Json,
    private val onShutdownRequested: () -> Unit,
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return try {
            route(method, uri, session)
        } catch (e: Exception) {
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                IpcCommandResponse(ok = false, error = e.message ?: "Unknown error")
            )
        }
    }

    private fun route(method: Method, uri: String, session: IHTTPSession): Response {
        return when {
            // --- Status ---
            method == Method.GET && uri == "/api/status" -> handleGetStatus()

            // --- Downloads ---
            method == Method.GET && uri == "/api/downloads" -> handleGetDownloads()
            method == Method.POST && uri == "/api/downloads/add" -> handleAddDownload(session)
            method == Method.GET && uri.matches(Regex("/api/downloads/\\d+")) -> handleGetDownload(uri)
            method == Method.POST && uri.matches(Regex("/api/downloads/\\d+/pause")) -> handlePauseDownload(uri)
            method == Method.POST && uri.matches(Regex("/api/downloads/\\d+/resume")) -> handleResumeDownload(uri)
            method == Method.POST && uri.matches(Regex("/api/downloads/\\d+/reset")) -> handleResetDownload(uri)
            method == Method.DELETE && uri.matches(Regex("/api/downloads/\\d+")) -> handleDeleteDownload(uri)

            // --- Queues ---
            method == Method.GET && uri == "/api/queues" -> handleGetQueues()
            method == Method.POST && uri == "/api/queues" -> handleCreateQueue(session)
            method == Method.POST && uri.matches(Regex("/api/queues/\\d+/start")) -> handleStartQueue(uri)
            method == Method.POST && uri.matches(Regex("/api/queues/\\d+/stop")) -> handleStopQueue(uri)
            method == Method.DELETE && uri.matches(Regex("/api/queues/\\d+")) -> handleDeleteQueue(uri)

            // --- Config ---
            method == Method.GET && uri == "/api/config" -> handleGetConfig()
            method == Method.PUT && uri == "/api/config" -> handleUpdateConfig(session)

            // --- Lifecycle ---
            method == Method.POST && uri == "/api/shutdown" -> handleShutdown()

            // --- SSE ---
            method == Method.GET && uri == "/api/events" -> handleSSE(session)

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    // --- Status ---

    private fun handleGetStatus(): Response {
        val status = IpcStatusResponse(
            ready = true,
            activeDownloads = di.downloadManager.getActiveCount(),
            totalDownloads = di.downloadManager.downloadJobs.size,
            version = "1.0.0",
            integrationPort = 15151,
        )
        return jsonResponse(Response.Status.OK, status)
    }

    // --- Downloads ---

    private fun handleGetDownloads(): Response {
        val downloads = runBlocking {
            di.downloadManager.getDownloadList().map { it.toIpcItem() }
        }
        return jsonResponse(Response.Status.OK, IpcDownloadListResponse(downloads))
    }

    private fun handleGetDownload(uri: String): Response {
        val id = extractId(uri) ?: return notFound()
        val item = runBlocking { di.downloadListDb.getById(id) }
            ?: return notFound()
        return jsonResponse(Response.Status.OK, item.toIpcItem())
    }

    private fun handleAddDownload(session: IHTTPSession): Response {
        val body = readBody(session)
        val request = json.decodeFromString<IpcAddDownloadRequest>(body)
        // TODO: Convert IpcNewDownloadItem to actual download credentials and add
        // For now return placeholder
        return jsonResponse(Response.Status.OK, IpcAddDownloadResponse(ids = emptyList()))
    }

    private fun handlePauseDownload(uri: String): Response {
        val id = extractIdBeforeAction(uri) ?: return notFound()
        runBlocking { di.downloadManager.pause(id) }
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    private fun handleResumeDownload(uri: String): Response {
        val id = extractIdBeforeAction(uri) ?: return notFound()
        runBlocking { di.downloadManager.resume(id) }
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    private fun handleResetDownload(uri: String): Response {
        val id = extractIdBeforeAction(uri) ?: return notFound()
        runBlocking { di.downloadManager.reset(id) }
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    private fun handleDeleteDownload(uri: String): Response {
        val id = extractId(uri) ?: return notFound()
        runBlocking { di.downloadManager.deleteDownload(id, { true }) }
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    // --- Queues ---

    private fun handleGetQueues(): Response {
        val queues = di.queueManager.getAll().map { queue ->
            val model = queue.getQueueModel()
            IpcQueueItem(
                id = model.id,
                name = model.name,
                isActive = queue.isQueueActive,
                itemCount = 0, // TODO: expose queue item count
            )
        }
        return jsonResponse(Response.Status.OK, IpcQueueListResponse(queues))
    }

    private fun handleCreateQueue(session: IHTTPSession): Response {
        val body = readBody(session)
        val request = json.decodeFromString<IpcCreateQueueRequest>(body)
        runBlocking { di.queueManager.addQueue(request.name) }
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    private fun handleStartQueue(uri: String): Response {
        val id = extractIdBeforeAction(uri) ?: return notFound()
        val queue = di.queueManager.getAll().find { it.getQueueModel().id == id }
            ?: return notFound()
        queue.start()
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    private fun handleStopQueue(uri: String): Response {
        val id = extractIdBeforeAction(uri) ?: return notFound()
        val queue = di.queueManager.getAll().find { it.getQueueModel().id == id }
            ?: return notFound()
        queue.stop()
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    private fun handleDeleteQueue(uri: String): Response {
        val id = extractId(uri) ?: return notFound()
        runBlocking { di.queueManager.deleteQueue(id) }
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    // --- Config ---

    private fun handleGetConfig(): Response {
        val config = IpcConfig(
            globalSpeedLimit = di.downloadSettings.globalSpeedLimit,
            defaultThreadCount = di.downloadSettings.defaultThreadCount,
            maxConcurrentDownloads = 0, // TODO: read from settings
            dynamicPartCreation = di.downloadSettings.dynamicPartCreationMode,
            useServerLastModifiedTime = di.downloadSettings.useServerLastModifiedTime,
            useSparseFileAllocation = di.downloadSettings.useSparseFileAllocation,
            maxDownloadRetryCount = di.downloadSettings.maxDownloadRetryCount,
            integrationEnabled = true,
            integrationPort = 15151,
            defaultDownloadFolder = System.getProperty("user.home") + "/Downloads",
        )
        return jsonResponse(Response.Status.OK, config)
    }

    private fun handleUpdateConfig(session: IHTTPSession): Response {
        val body = readBody(session)
        val config = json.decodeFromString<IpcConfig>(body)
        // Apply config changes
        di.downloadSettings.globalSpeedLimit = config.globalSpeedLimit
        di.downloadSettings.defaultThreadCount = config.defaultThreadCount
        di.downloadSettings.dynamicPartCreationMode = config.dynamicPartCreation
        di.downloadSettings.useServerLastModifiedTime = config.useServerLastModifiedTime
        di.downloadSettings.useSparseFileAllocation = config.useSparseFileAllocation
        di.downloadSettings.maxDownloadRetryCount = config.maxDownloadRetryCount
        di.downloadManager.limitGlobalSpeed(config.globalSpeedLimit)
        di.downloadManager.reloadSetting()
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    // --- Lifecycle ---

    private fun handleShutdown(): Response {
        onShutdownRequested()
        return jsonResponse(Response.Status.OK, IpcCommandResponse(ok = true))
    }

    // --- SSE (Server-Sent Events) ---

    private fun handleSSE(session: IHTTPSession): Response {
        // SSE requires chunked response - NanoHTTPD supports this via ChunkedResponse
        // For now, return a simple placeholder. Full SSE implementation requires
        // a background thread pushing events to the response stream.
        // TODO: Implement full SSE with download progress events
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "text/event-stream",
            "data: {\"type\":\"connected\"}\n\n"
        )
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        return response
    }

    // --- Helpers ---

    private inline fun <reified T> jsonResponse(status: Response.Status, body: T): Response {
        val jsonStr = json.encodeToString(body)
        return newFixedLengthResponse(status, "application/json", jsonStr)
    }

    private fun notFound(): Response {
        return jsonResponse(Response.Status.NOT_FOUND, IpcCommandResponse(ok = false, error = "Not found"))
    }

    private fun readBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer, 0, contentLength)
        return String(buffer)
    }

    /** Extract ID from URI like /api/downloads/123 */
    private fun extractId(uri: String): Long? {
        return uri.substringAfterLast("/").toLongOrNull()
    }

    /** Extract ID from URI like /api/downloads/123/pause */
    private fun extractIdBeforeAction(uri: String): Long? {
        val parts = uri.split("/")
        return parts.getOrNull(parts.size - 2)?.toLongOrNull()
    }

    private fun com.flowspeed.lib.downloader.downloaditem.IDownloadItem.toIpcItem(): IpcDownloadItem {
        return IpcDownloadItem(
            id = id,
            name = name,
            url = link,
            folder = folder,
            status = status.name,
            contentLength = contentLength,
            downloadedSize = 0, // Would need DownloadJob to get real progress
            speed = 0,
            dateAdded = dateAdded,
            startTime = startTime,
            completeTime = completeTime,
        )
    }
}
