package com.flowspeed.link.desktop.integration

import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.flowspeed.link.shared.pages.adddownload.ImportOptions
import com.flowspeed.lib.downloader.downloaditem.http.HttpDownloadCredentials
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Polls the Go service IPC endpoint for pending download requests.
 *
 * When the Go service receives a download request from the browser extension
 * and the UI is connected, it queues the request. This poller picks up those
 * requests and shows the add-download dialog in the Kotlin UI.
 */
class ServiceIPCPoller : KoinComponent {
    private val appComponent by inject<AppComponent>()
    private var pollingJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class PendingItem(
        val type: String = "http",
        val link: String,
        val headers: Map<String, String>? = null,
        val downloadPage: String? = null,
        val name: String? = null,
        val folder: String? = null,
    )

    /**
     * Starts polling the service for pending downloads.
     * Should be called after IPC connect.
     */
    fun start(scope: CoroutineScope) {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val items = fetchPending()
                    if (items.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            handlePendingItems(items)
                        }
                    }
                } catch (_: Exception) {
                    // Service might be unavailable, retry after delay
                }
                delay(500) // Poll every 500ms
            }
        }
    }

    /** Stops polling. */
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun fetchPending(): List<PendingItem> {
        val url = URI("http://127.0.0.1:15152/api/ui/pending")
        val conn = url.toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2000
        conn.readTimeout = 2000

        return try {
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                if (body.isNotBlank() && body != "null") {
                    json.decodeFromString<List<PendingItem>>(body)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun handlePendingItems(items: List<PendingItem>) {
        val credentials = items.map { item ->
            AddDownloadCredentialsInUiProps(
                credentials = HttpDownloadCredentials(
                    link = item.link,
                    headers = item.headers,
                    downloadPage = item.downloadPage,
                ),
                extraConfig = AddDownloadCredentialsInUiProps.Configs(
                    suggestedName = item.name,
                )
            )
        }
        appComponent.externalCredentialComingIntoApp(
            credentials,
            options = ImportOptions(silentImport = null)
        )
    }
}
