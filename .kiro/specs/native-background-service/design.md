# Technical Design: Native Background Service

## Architecture Overview

```
┌─────────────────────────────────────┐
│  NATIVE SERVICE (GraalVM native)    │
│  Target: ~20-30MB idle RAM          │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Integration HTTP Server       │  │
│  │ (NanoHTTPD, port 15151)       │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ IPC Server                    │  │
│  │ (HTTP localhost:15152)        │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Download Engine               │  │
│  │ (Java HttpClient, coroutines) │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Queue Manager                 │  │
│  │ (ManualDownloadQueue)         │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Persistence Layer             │  │
│  │ (File-based JSON storage)     │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
         ▲ IPC (HTTP)
         │
         ▼
┌─────────────────────────────────────┐
│  UI PROCESS (Compose Desktop/JVM)   │
│  On-demand only (~150MB when open)  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ IPC Client                    │  │
│  │ (connects to service)         │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Compose UI                    │  │
│  │ (existing AppComponent)       │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ System Tray (AWT)             │  │
│  │ (lightweight, always visible) │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## Module Structure

```
desktop/
├── service/              ← NEW: Native background service
│   ├── build.gradle.kts  (GraalVM native-image plugin)
│   └── src/main/kotlin/
│       └── com/flowspeed/link/service/
│           ├── ServiceMain.kt          (entry point)
│           ├── ServiceDi.kt            (manual DI, no Koin)
│           ├── ipc/
│           │   ├── IpcServer.kt        (HTTP server for UI communication)
│           │   ├── IpcProtocol.kt      (request/response models)
│           │   └── IpcRoutes.kt        (route handlers)
│           ├── lifecycle/
│           │   ├── ServiceLifecycle.kt
│           │   ├── SingleInstanceLock.kt
│           │   └── AutoStartRegistrar.kt
│           └── integration/
│               └── BrowserIntegrationServer.kt
│
├── app/                  ← MODIFIED: UI-only process
│   └── src/main/kotlin/
│       └── com/flowspeed/link/desktop/
│           ├── App.kt                  (launches UI, connects to service)
│           ├── ipc/
│           │   ├── IpcClient.kt        (connects to service)
│           │   └── RemoteDownloadSystem.kt (proxy for DownloadSystem)
│           └── ...existing UI code...
```

## Key Design Decisions

### 1. HTTP Client Replacement
- **Current**: OkHttp 5.x (not GraalVM-friendly without extensive config)
- **New**: Java 11+ `java.net.http.HttpClient` (built into JVM, works natively)
- **Scope**: Only in `downloader:core` module, behind `HttpDownloaderClient` interface

### 2. DI Strategy for Service
- **Current**: Koin (reflection-based)
- **New**: Manual constructor injection in `ServiceDi.kt`
- **Reason**: GraalVM native-image doesn't support runtime reflection well

### 3. IPC Protocol
- **Transport**: HTTP on localhost (port 15152)
- **Format**: JSON (kotlinx.serialization)
- **Endpoints**:
  - `GET /status` → service health + active download count
  - `GET /downloads` → full download list state
  - `GET /downloads/{id}/progress` → single download progress
  - `POST /downloads/add` → add new download
  - `POST /downloads/{id}/pause` → pause download
  - `POST /downloads/{id}/resume` → resume download
  - `DELETE /downloads/{id}` → delete download
  - `GET /config` → current configuration
  - `PUT /config` → update configuration
  - `WS /events` → WebSocket for real-time push (or SSE)

### 4. System Tray
- **Service process**: No tray (headless)
- **UI process**: AWT tray (lightweight, already implemented in LightweightTray.kt)
- Tray always runs in a minimal JVM launcher (~6MB) that monitors service and launches UI

## Migration Strategy

Phase 1: Create `desktop:service` module with manual DI
Phase 2: Replace OkHttp with Java HttpClient in downloader:core
Phase 3: Implement IPC protocol
Phase 4: GraalVM native-image build configuration
Phase 5: Modify UI process to use IPC client
Phase 6: Platform-specific lifecycle (auto-start, crash recovery)
Phase 7: Migration from single-process data format
