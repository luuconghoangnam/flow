package com.flowspeed.link.service.ipc

import kotlinx.serialization.Serializable

/**
 * IPC data models for communication between Background Service and UI Process.
 * All models use kotlinx.serialization for GraalVM native-image compatibility.
 */

// --- Status ---

@Serializable
data class IpcStatusResponse(
    val ready: Boolean,
    val activeDownloads: Int,
    val totalDownloads: Int,
    val version: String,
    val integrationPort: Int,
)

// --- Download State ---

@Serializable
data class IpcDownloadItem(
    val id: Long,
    val name: String,
    val url: String,
    val folder: String,
    val status: String, // Added, Downloading, Paused, Completed, Error
    val contentLength: Long,
    val downloadedSize: Long,
    val speed: Long,
    val dateAdded: Long,
    val startTime: Long?,
    val completeTime: Long?,
)

@Serializable
data class IpcDownloadListResponse(
    val downloads: List<IpcDownloadItem>,
)

// --- Add Download ---

@Serializable
data class IpcAddDownloadRequest(
    val items: List<IpcNewDownloadItem>,
    val queueId: Long? = null,
    val silentAdd: Boolean = false,
    val silentStart: Boolean = false,
)

@Serializable
data class IpcNewDownloadItem(
    val type: String = "http", // "http" or "hls"
    val link: String,
    val headers: Map<String, String>? = null,
    val downloadPage: String? = null,
    val name: String? = null,
    val folder: String? = null,
)

@Serializable
data class IpcAddDownloadResponse(
    val ids: List<Long>,
)

// --- Download Commands ---

@Serializable
data class IpcCommandResponse(
    val ok: Boolean,
    val error: String? = null,
)

// --- Queue ---

@Serializable
data class IpcQueueItem(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val itemCount: Int,
)

@Serializable
data class IpcQueueListResponse(
    val queues: List<IpcQueueItem>,
)

@Serializable
data class IpcCreateQueueRequest(
    val name: String,
)

@Serializable
data class IpcCreateQueueResponse(
    val id: Long,
)

// --- Configuration ---

@Serializable
data class IpcConfig(
    val globalSpeedLimit: Long,
    val defaultThreadCount: Int,
    val maxConcurrentDownloads: Int,
    val dynamicPartCreation: Boolean,
    val useServerLastModifiedTime: Boolean,
    val useSparseFileAllocation: Boolean,
    val maxDownloadRetryCount: Int,
    val integrationEnabled: Boolean,
    val integrationPort: Int,
    val defaultDownloadFolder: String,
)

// --- SSE Events ---

@Serializable
data class IpcProgressEvent(
    val id: Long,
    val downloadedSize: Long,
    val speed: Long,
    val status: String,
)

@Serializable
data class IpcDownloadEvent(
    val type: String, // "added", "completed", "error", "removed", "started", "paused"
    val downloadId: Long,
    val download: IpcDownloadItem? = null,
    val error: String? = null,
)
