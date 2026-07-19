# Implementation Tasks

> **Status: DEFERRED** — All phases untouched. This is a large spec requiring GraalVM native-image setup, IPC protocol, and lifecycle management. Will be picked up in a dedicated future sprint.
>
> The Inno installer (created Jul 2026) replaces the NSIS installer referenced in Phase 7.2.

## Phase 1: Service Module Foundation [REQ 1, 2, 3]

- [ ] 1.1 Create `desktop/service/build.gradle.kts`
  - Kotlin JVM plugin
  - kotlinx.serialization plugin
  - Dependencies: kotlinx-coroutines-core, kotlinx-serialization-json, NanoHTTPD (fi.iki.elonen)
  - GraalVM native-image plugin (org.graalvm.buildtools.native)
  - Reference existing `downloader:core`, `downloader:monitor`, `shared:app`, `shared:utils`, `shared:local-server`, `integration:server`
  - Configure mainClass for native-image

- [ ] 1.2 Register `desktop/service` in `settings.gradle.kts`
  - Add `include(":desktop:service")` to project includes

- [ ] 1.3 Create `ServiceMain.kt` entry point
  - Parse CLI args: `--port` (integration), `--ipc-port`, `--data-dir`, `--background`
  - Initialize SingleInstanceLock
  - If lock acquired → boot service
  - If lock failed → forward args to running instance, exit
  - Register shutdown hook for graceful stop

- [ ] 1.4 Create `SingleInstanceLock.kt`
  - Use `java.nio.channels.FileLock` on a lock file in data dir
  - Write PID + IPC port to lock file on acquire
  - Detect stale locks (check if PID is alive)
  - Provide `getRunningInstancePort(): Int?` for other processes to connect

- [ ] 1.5 Create `ServiceDi.kt` (manual dependency injection)
  - No Koin, no reflection
  - Wire: DefinedPaths, Json, DownloadFoldersRegistry, TransactionalFileSaver
  - Wire: IDownloadListDb, IDownloadPartListDb, IDownloadQueueDatabase
  - Wire: DownloadSettings, EmptyFileCreator, HttpDownloaderClient
  - Wire: DownloaderRegistry, DownloadManager, QueueManager, ManualDownloadQueue
  - Wire: DownloadMonitor, DownloadSystem, CategoryManager
  - Wire: Integration server, IPC server
  - All as lazy vals (initialized on first access)

- [ ] 1.6 Create `ServiceLifecycle.kt`
  - `boot()`: Initialize DI → boot DownloadSystem → start Integration server → start IPC server
  - `shutdown()`: Stop active downloads (with timeout) → persist state → stop servers → release lock
  - `isReady(): Boolean` for health checks
  - Handle SIGTERM/SIGINT gracefully

## Phase 2: Java HttpClient Implementation [REQ 6, 9]

- [ ] 2.1 Create `JavaHttpDownloaderClient.kt` in `downloader:core`
  - Implement `HttpDownloaderClient` abstract class
  - Use `java.net.http.HttpClient` (built into JDK 11+, no extra deps)
  - Support: Range headers, custom headers, User-Agent, Basic Auth
  - Support: proxy (HTTP, SOCKS) via `ProxySelector`
  - Support: SSL configuration (trust all option)
  - Return `Connection<HttpResponseInfo>` with response body as okio `Source`

- [ ] 2.2 Bridge `java.net.http` response body to okio `Source`
  - Create `InputStreamSource.kt` adapter: `InputStream` → okio `Source`
  - Handle content-length reporting
  - Ensure proper close/cleanup

- [ ] 2.3 Implement proxy support in `JavaHttpDownloaderClient`
  - Map `ProxyStrategy` → `java.net.ProxySelector`
  - Support Direct, System, Manual (HTTP/SOCKS), PAC script
  - Cache proxy-configured HttpClient instances (same pattern as OkHttp version)

- [ ] 2.4 Implement SSL configuration
  - Create `SSLContext` with custom TrustManager when "ignore SSL" is enabled
  - Pass to `HttpClient.Builder().sslContext()`

- [ ] 2.5 Add factory/switch to select HTTP client implementation
  - In `downloader:core`, add `HttpDownloaderClientFactory` interface
  - Desktop service uses `JavaHttpDownloaderClient`
  - Desktop UI + Android continue using `OkHttpHttpDownloaderClient`
  - Selection via constructor parameter (no reflection)

- [ ] 2.6 Test download operations with Java HttpClient
  - Multi-part download with Range headers
  - Resume interrupted download
  - Download with proxy
  - Download with custom headers
  - HLS download (playlist + segments)

## Phase 3: IPC Protocol & Server [REQ 5, 7, 8]

- [ ] 3.1 Define IPC data models in shared module
  - Create `shared/ipc/` module or package
  - `IpcDownloadState`: id, name, url, status, progress, speed, folder, size
  - `IpcQueueState`: id, name, items, isActive
  - `IpcConfig`: speedLimit, threadCount, proxySettings, integrationPort, etc.
  - `IpcCommand`: sealed class (AddDownload, Pause, Resume, Delete, UpdateConfig, etc.)
  - `IpcEvent`: sealed class (DownloadProgress, DownloadCompleted, DownloadError, ConfigChanged)
  - All annotated with `@Serializable`

- [ ] 3.2 Create `IpcServer.kt` in service module
  - NanoHTTPD-based HTTP server on configurable port (default 15152)
  - Only accept localhost connections
  - JSON request/response using kotlinx.serialization
  - Route dispatcher matching request path + method

- [ ] 3.3 Implement IPC routes - Download management
  - `GET /api/status` → { ready: bool, activeDownloads: int, version: string }
  - `GET /api/downloads` → List<IpcDownloadState>
  - `GET /api/downloads/{id}` → IpcDownloadState
  - `POST /api/downloads/add` → { items: [...], options: {...} } → { ids: [...] }
  - `POST /api/downloads/{id}/pause` → { ok: bool }
  - `POST /api/downloads/{id}/resume` → { ok: bool }
  - `DELETE /api/downloads/{id}` → { ok: bool }
  - `POST /api/downloads/{id}/reset` → { ok: bool }

- [ ] 3.4 Implement IPC routes - Queue management
  - `GET /api/queues` → List<IpcQueueState>
  - `POST /api/queues` → { name: string } → { id: long }
  - `POST /api/queues/{id}/start` → { ok: bool }
  - `POST /api/queues/{id}/stop` → { ok: bool }
  - `DELETE /api/queues/{id}` → { ok: bool }

- [ ] 3.5 Implement IPC routes - Configuration
  - `GET /api/config` → IpcConfig
  - `PUT /api/config` → IpcConfig → { ok: bool }
  - Apply config changes to DownloadSettings, Integration, ProxyManager immediately

- [ ] 3.6 Implement Server-Sent Events (SSE) for real-time updates
  - `GET /api/events` → SSE stream
  - Push events: download_progress (every 500ms for active downloads), download_completed, download_error, download_added, config_changed
  - Client reconnection support (Last-Event-ID header)
  - Cleanup disconnected clients

## Phase 4: GraalVM Native Image Build [REQ 9, 10]

- [ ] 4.1 Add GraalVM native-image plugin to `desktop/service/build.gradle.kts`
  - Plugin: `org.graalvm.buildtools.native` 
  - Configure: mainClass, imageName, buildArgs
  - Add `--no-fallback`, `--enable-url-protocols=http,https`
  - Add `--initialize-at-build-time` for kotlinx.serialization

- [ ] 4.2 Create `META-INF/native-image/` configuration files
  - `reflect-config.json`: kotlinx.serialization generated serializers
  - `resource-config.json`: any bundled resources (app.properties, etc.)
  - `proxy-config.json`: if any dynamic proxies are used
  - `serialization-config.json`: for Java serialization if needed

- [ ] 4.3 Resolve native-image incompatibilities
  - Replace any `Class.forName()` calls with direct references
  - Replace any `ServiceLoader` usage with explicit registration
  - Ensure all coroutine dispatchers work in native (Dispatchers.IO uses ForkJoinPool)
  - Test NanoHTTPD in native-image (uses threads, should work)

- [ ] 4.4 Create build tasks for each platform
  - Windows x64: `nativeCompile` on Windows runner
  - Linux x64: `nativeCompile` on Linux runner
  - macOS x64 + ARM64: `nativeCompile` on macOS runners
  - Output: single executable per platform (~20-40MB binary)

- [ ] 4.5 Benchmark and optimize native service
  - Measure startup time (target: <2s)
  - Measure idle RSS (target: ≤30MB)
  - Measure memory during active download (target: ≤50MB per download)
  - Profile with `native-image --pgo` if needed

## Phase 5: Modify UI Process [REQ 5, 11]

- [ ] 5.1 Create `IpcClient.kt` in `desktop/app`
  - HTTP client connecting to service IPC port
  - Auto-discover port from lock file
  - Retry connection with backoff
  - SSE listener for real-time events
  - Connection state flow (Connected, Disconnected, Reconnecting)

- [ ] 5.2 Create `RemoteDownloadSystem.kt`
  - Implements same interface as `DownloadSystem` (or a subset)
  - All operations proxy to IPC: addDownload, pause, resume, delete, getState
  - Exposes StateFlows populated from SSE events
  - Handles disconnection gracefully (show "service unavailable" in UI)

- [ ] 5.3 Create `RemoteAppRepository.kt`
  - Reads/writes config via IPC `/api/config`
  - Exposes same StateFlows as current `AppRepository`
  - Syncs settings changes bidirectionally

- [ ] 5.4 Modify `AppBootstrapper.kt` for UI-only mode
  - Remove: Di.boot() for download engine modules
  - Add: Connect to service via IpcClient
  - Add: Check service health, show error if not running
  - Keep: UI modules (ThemeManager, FontManager, LanguageManager, AppComponent)

- [ ] 5.5 Modify `UiModule.kt` DI
  - Remove: downloaderModule, downloadSystemModule, integrationModule
  - Add: IpcClient, RemoteDownloadSystem, RemoteAppRepository
  - Keep: storageModule (for UI-specific settings like theme, font)
  - Keep: uiModule (AppComponent, etc.)

- [ ] 5.6 Update system tray behavior
  - Tray launcher (tiny JVM or native) always runs
  - Shows service status icon
  - Click → launch UI process (if not running)
  - Right-click → menu (Show, Settings, Exit)
  - "Exit" sends shutdown to service + exits tray

## Phase 6: Platform Lifecycle [REQ 2, 10]

- [ ] 6.1 Windows auto-start
  - Register service in `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
  - Or create shortcut in Startup folder
  - Service binary path with `--background` flag

- [ ] 6.2 Linux auto-start
  - Generate `~/.config/systemd/user/flow-service.service` file
  - Or `~/.config/autostart/flow-service.desktop` (XDG)
  - `systemctl --user enable flow-service`

- [ ] 6.3 macOS auto-start
  - Generate `~/Library/LaunchAgents/link.flowspeed.service.plist`
  - `launchctl load` on install
  - KeepAlive = true for crash recovery

- [ ] 6.4 Crash recovery
  - Tray launcher monitors service process
  - If service exits unexpectedly → restart after 3s delay
  - Max 5 restarts in 60s before giving up (show error to user)
  - Log crash events to file

- [ ] 6.5 Graceful shutdown implementation
  - On SIGTERM: pause all active downloads, save state, exit
  - On "Exit" from UI: complete active downloads (with 30s timeout), then exit
  - On "Force Exit": save state immediately, kill active connections, exit

## Phase 7: Migration & Packaging [REQ 12]

- [ ] 7.1 Data migration on first service startup
  - Detect legacy data dir (same location, check for absence of service lock file)
  - Copy download list, parts, queues, settings to service-owned format
  - Backup original files to `.backup/` subfolder
  - Log migration results

- [ ] 7.2 Update NSIS installer (Windows)
  - Install both: `Flow.exe` (UI) and `flow-service.exe` (native service)
  - Register service auto-start during install
  - Uninstall: stop service, remove auto-start, delete files

- [ ] 7.3 Update DMG/PKG installer (macOS)
  - Bundle both binaries in .app
  - Install launchd plist
  - Post-install script to load service

- [ ] 7.4 Update Linux packaging (Deb/AppImage)
  - Include service binary
  - Install systemd user service file
  - Post-install: enable service

- [ ] 7.5 Update GitHub Actions workflow
  - Add GraalVM setup step
  - Build native-image for each platform matrix entry
  - Include service binary in release artifacts
  - Update artifact naming

- [ ] 7.6 End-to-end integration test
  - Start service → verify health endpoint
  - Send download via extension API → verify download starts
  - Launch UI → verify it connects and shows progress
  - Close UI → verify service continues downloading
  - Reopen UI → verify state is synced
  - Stop service → verify graceful shutdown and state persistence
