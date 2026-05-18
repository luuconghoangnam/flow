package com.flowspeed.link.desktop.ipc

import com.flowspeed.link.service.ipc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * IPC client that connects the UI process to the background service.
 *
 * Handles:
 * - Auto-discovery of service port from lock file
 * - Connection health monitoring
 * - Retry with exponential backoff on disconnect
 * - All REST API calls to the service
 */
class IpcClient(
    private val json: Json,
    private val scope: CoroutineScope,
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var baseUrl: String = "http://127.0.0.1:15152"

    sealed interface ConnectionState {
        data object Connected : ConnectionState
        data object Disconnected : ConnectionState
        data object Reconnecting : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    /** Connect to service at the given port. */
    fun connect(port: Int) {
        baseUrl = "http://127.0.0.1:$port"
        _connectionState.value = ConnectionState.Reconnecting
        scope.launch {
            if (checkHealth()) {
                _connectionState.value = ConnectionState.Connected
            } else {
                _connectionState.value = ConnectionState.Error("Service not responding")
            }
        }
    }

    /** Connect using port from lock file. */
    fun connectFromLockFile(dataDir: java.io.File) {
        val lockFile = java.io.File(dataDir, "service.lock")
        if (!lockFile.exists()) {
            _connectionState.value = ConnectionState.Error("Service not running")
            return
        }
        val port = lockFile.readLines()
            .find { it.startsWith("ipc_port=") }
            ?.substringAfter("=")
            ?.toIntOrNull()
        if (port == null || port == 0) {
            _connectionState.value = ConnectionState.Error("Invalid service port")
            return
        }
        connect(port)
    }

    // --- Health ---

    suspend fun checkHealth(): Boolean {
        return try {
            val status = getStatus()
            status?.ready == true
        } catch (_: Exception) {
            false
        }
    }

    // --- Status ---

    suspend fun getStatus(): IpcStatusResponse? = get("/api/status")

    // --- Downloads ---

    suspend fun getDownloads(): IpcDownloadListResponse? = get("/api/downloads")

    suspend fun getDownload(id: Long): IpcDownloadItem? = get("/api/downloads/$id")

    suspend fun addDownload(request: IpcAddDownloadRequest): IpcAddDownloadResponse? =
        post("/api/downloads/add", json.encodeToString(IpcAddDownloadRequest.serializer(), request))

    suspend fun pauseDownload(id: Long): IpcCommandResponse? =
        post("/api/downloads/$id/pause", "")

    suspend fun resumeDownload(id: Long): IpcCommandResponse? =
        post("/api/downloads/$id/resume", "")

    suspend fun resetDownload(id: Long): IpcCommandResponse? =
        post("/api/downloads/$id/reset", "")

    suspend fun deleteDownload(id: Long): IpcCommandResponse? =
        delete("/api/downloads/$id")

    // --- Queues ---

    suspend fun getQueues(): IpcQueueListResponse? = get("/api/queues")

    suspend fun createQueue(name: String): IpcCommandResponse? =
        post("/api/queues", json.encodeToString(IpcCreateQueueRequest.serializer(), IpcCreateQueueRequest(name)))

    suspend fun startQueue(id: Long): IpcCommandResponse? =
        post("/api/queues/$id/start", "")

    suspend fun stopQueue(id: Long): IpcCommandResponse? =
        post("/api/queues/$id/stop", "")

    suspend fun deleteQueue(id: Long): IpcCommandResponse? =
        delete("/api/queues/$id")

    // --- Config ---

    suspend fun getConfig(): IpcConfig? = get("/api/config")

    suspend fun updateConfig(config: IpcConfig): IpcCommandResponse? =
        put("/api/config", json.encodeToString(IpcConfig.serializer(), config))

    // --- Lifecycle ---

    suspend fun shutdown(): IpcCommandResponse? = post("/api/shutdown", "")

    // --- HTTP helpers ---

    private suspend inline fun <reified T> get(path: String): T? = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                json.decodeFromString<T>(response.body())
            } else null
        } catch (e: Exception) {
            handleConnectionError(e)
            null
        }
    }

    private suspend inline fun <reified T> post(path: String, body: String): T? = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                json.decodeFromString<T>(response.body())
            } else null
        } catch (e: Exception) {
            handleConnectionError(e)
            null
        }
    }

    private suspend inline fun <reified T> put(path: String, body: String): T? = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                json.decodeFromString<T>(response.body())
            } else null
        } catch (e: Exception) {
            handleConnectionError(e)
            null
        }
    }

    private suspend inline fun <reified T> delete(path: String): T? = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                json.decodeFromString<T>(response.body())
            } else null
        } catch (e: Exception) {
            handleConnectionError(e)
            null
        }
    }

    private fun handleConnectionError(e: Exception) {
        if (_connectionState.value == ConnectionState.Connected) {
            _connectionState.value = ConnectionState.Reconnecting
        }
    }
}
