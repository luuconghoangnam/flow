# Requirements Document

## Introduction

This feature splits the Flow Download Manager desktop application into two separate processes to reduce idle memory consumption. Currently, the app runs as a single JVM process (~136-260MB RAM) even when minimized to the system tray with no active downloads. The goal is to extract the download engine, queue manager, browser integration HTTP server, and file I/O into a lightweight native background service compiled with GraalVM native-image (~20-30MB idle RAM), while the existing Compose Desktop UI process starts on-demand only when user interaction is needed.

## Glossary

- **Background_Service**: The native-compiled process responsible for download execution, queue management, browser extension communication, and file I/O. Compiled with GraalVM native-image from existing Kotlin code.
- **UI_Process**: The existing Compose Desktop JVM application that provides the graphical user interface. Starts on-demand and communicates with the Background_Service via IPC.
- **IPC_Channel**: The inter-process communication mechanism (HTTP on localhost or Unix domain socket) used for bidirectional communication between the Background_Service and UI_Process.
- **Integration_Server**: The HTTP server within the Background_Service that listens on localhost for browser extension requests (currently using http4k/Sun HTTP server).
- **Download_Engine**: The subsystem responsible for multi-threaded, multi-part HTTP downloads with resume support (currently using OkHttp as HTTP client).
- **Queue_Manager**: The subsystem that manages download scheduling, concurrency limits, and queue persistence.
- **Single_Instance_Lock**: A mechanism ensuring only one instance of the Background_Service runs at a time per user session.
- **Service_Lifecycle_Manager**: The component responsible for starting, stopping, and monitoring the Background_Service process.
- **Native_Image**: A standalone executable produced by GraalVM native-image compilation, requiring no JVM at runtime.

## Requirements

### Requirement 1: Process Architecture Separation

**User Story:** As a user, I want the download engine to run as a separate lightweight process, so that my system uses minimal RAM when no UI is visible and no downloads are active.

#### Acceptance Criteria

1. THE Background_Service SHALL operate as an independent OS process separate from the UI_Process
2. WHEN no downloads are active and no UI_Process is connected, THE Background_Service SHALL consume no more than 30MB of resident memory
3. WHILE downloads are active, THE Background_Service SHALL be permitted to exceed the 30MB memory limit as needed for download operations
4. THE Background_Service SHALL be compiled as a Native_Image using GraalVM native-image
5. WHEN the Background_Service starts, THE Background_Service SHALL initialize the Download_Engine, Queue_Manager, and Integration_Server without requiring the UI_Process
6. THE UI_Process SHALL start only when the user explicitly requests the graphical interface

### Requirement 2: Background Service Lifecycle Management

**User Story:** As a user, I want the background service to start automatically at system boot and remain running, so that browser extension downloads work without manually opening the app.

#### Acceptance Criteria

1. WHEN the operating system starts a user session, THE Service_Lifecycle_Manager SHALL start the Background_Service automatically
2. WHILE the Background_Service is running, THE Background_Service SHALL respond to health check requests from the UI_Process within 500ms
3. IF the Background_Service crashes, THEN THE Service_Lifecycle_Manager SHALL restart the Background_Service within 5 seconds
4. WHEN the user requests application exit from the UI_Process, THE Background_Service SHALL complete all active downloads before shutting down
5. WHEN the user requests a forced exit, THE Background_Service SHALL immediately persist download progress and shut down within 2 seconds without waiting for active downloads to complete
6. THE Background_Service SHALL register platform-appropriate auto-start mechanisms (Windows: registry/startup folder, Linux: systemd user service or XDG autostart, macOS: launchd user agent)

### Requirement 3: Single Instance Enforcement

**User Story:** As a user, I want only one background service instance running at a time, so that downloads are not duplicated and port conflicts do not occur.

#### Acceptance Criteria

1. WHEN the Background_Service starts, THE Single_Instance_Lock SHALL acquire an exclusive lock before initializing subsystems
2. IF another Background_Service instance is already running, THEN THE Background_Service SHALL forward any startup arguments to the existing instance and exit, regardless of whether the forwarding succeeds
3. WHEN the Background_Service acquires the Single_Instance_Lock, THE Background_Service SHALL write its process ID and IPC_Channel address to a lock file
4. IF the Single_Instance_Lock file references a stale process that is no longer running, THEN THE Background_Service SHALL reclaim the lock

### Requirement 4: Browser Extension Integration

**User Story:** As a user, I want the browser extension to send download requests to the background service, so that downloads start immediately without opening the UI.

#### Acceptance Criteria

1. THE Integration_Server SHALL listen on a configurable localhost port (default 15151) for HTTP requests from the browser extension
2. WHEN the Integration_Server receives a POST request to /add, THE Background_Service SHALL parse the JSON payload and add the download to the Download_Engine
3. WHEN the Integration_Server receives a POST request to /start-headless-download, THE Background_Service SHALL create and immediately start the download
4. WHEN the Integration_Server receives a GET request to /queues, THE Background_Service SHALL return the list of available queues as JSON
5. WHEN the Integration_Server receives a POST request to /ping, THE Background_Service SHALL respond with "pong" within 100ms
6. THE Integration_Server SHALL accept only connections from localhost (127.0.0.1 and ::1)

### Requirement 5: IPC Between Background Service and UI Process

**User Story:** As a user, I want the UI to reflect real-time download progress from the background service, so that I can monitor downloads when the UI is open.

#### Acceptance Criteria

1. THE IPC_Channel SHALL support bidirectional communication between the Background_Service and UI_Process
2. WHEN the UI_Process connects to the Background_Service, THE Background_Service SHALL send the current state of all downloads within 1 second, or fail the connection if the deadline cannot be met
3. WHILE the UI_Process is connected, THE Background_Service SHALL push download progress updates at intervals no greater than 500ms
4. WHEN the UI_Process sends a command (pause, resume, delete, add), THE Background_Service SHALL acknowledge receipt within 200ms and report execution results separately
5. IF the UI_Process disconnects unexpectedly, THEN THE Background_Service SHALL continue all active downloads without interruption
6. WHEN the UI_Process reconnects after disconnection, THE Background_Service SHALL resynchronize the full download state
7. THE IPC_Channel SHALL use a localhost HTTP or Unix domain socket transport that does not require network access

### Requirement 6: Download Engine in Native Image

**User Story:** As a developer, I want the download engine to function correctly within a GraalVM native-image, so that the background service achieves low memory usage.

#### Acceptance Criteria

1. THE Download_Engine SHALL perform multi-part concurrent downloads with configurable part count within the Native_Image
2. THE Download_Engine SHALL support download pause and resume by persisting part progress to disk
3. THE Download_Engine SHALL support HTTP and HLS download protocols within the Native_Image
4. WHEN a download completes, THE Download_Engine SHALL verify file integrity and update the persistent download list
5. THE Download_Engine SHALL use an HTTP client compatible with GraalVM native-image (replacing OkHttp if necessary with a native-compatible alternative such as Java 11+ HttpClient)
6. THE Queue_Manager SHALL enforce per-queue concurrency limits and scheduling within the Native_Image

### Requirement 7: Data Persistence and Shared State

**User Story:** As a user, I want my download history and queue configuration to be accessible from both the service and UI, so that state is consistent regardless of which process I interact with.

#### Acceptance Criteria

1. THE Background_Service SHALL be the single owner of all download state persistence (download list, part progress, queue configuration)
2. THE UI_Process SHALL read download state exclusively through the IPC_Channel, not by directly accessing persistence files
3. WHEN the Background_Service persists download state changes, THE Background_Service SHALL use atomic file operations to prevent corruption
4. IF the Background_Service detects corrupted persistence files on startup, THEN THE Background_Service SHALL attempt recovery from the last known good state and log the corruption event

### Requirement 8: Configuration Management

**User Story:** As a user, I want to configure download settings (speed limits, concurrent downloads, proxy) from the UI and have them apply immediately in the background service.

#### Acceptance Criteria

1. WHEN the UI_Process sends a configuration change via IPC_Channel, THE Background_Service SHALL apply the new configuration within 1 second or reject the change with an error if the deadline cannot be met
2. THE Background_Service SHALL persist configuration changes to disk so they survive restarts
3. WHEN the Background_Service starts, THE Background_Service SHALL load the last persisted configuration
4. THE Background_Service SHALL support configuration of: global speed limit, per-download part count, proxy settings, Integration_Server port, and per-queue concurrency limits

### Requirement 9: GraalVM Native Image Compatibility

**User Story:** As a developer, I want the background service codebase to be compatible with GraalVM native-image compilation, so that the service starts quickly and uses minimal memory.

#### Acceptance Criteria

1. THE Background_Service SHALL start and be ready to accept requests within 2 seconds of process launch
2. THE Background_Service SHALL not use reflection-based dependency injection at runtime (replacing Koin with compile-time wiring or manual DI)
3. THE Background_Service SHALL use kotlinx.serialization for all JSON parsing and generation
4. THE Background_Service SHALL provide GraalVM reflection configuration for any libraries that require it
5. IF a library is incompatible with native-image compilation, THEN THE Background_Service SHALL replace it with a native-compatible alternative

### Requirement 10: Cross-Platform Support

**User Story:** As a user, I want the background service to work on Windows, Linux, and macOS, so that I have the same experience regardless of my operating system.

#### Acceptance Criteria

1. THE Background_Service SHALL compile and run as a Native_Image on Windows (x64), Linux (x64), and macOS (x64 and ARM64)
2. THE Single_Instance_Lock SHALL use platform-appropriate locking mechanisms (file locks on all platforms)
3. THE Service_Lifecycle_Manager SHALL use platform-appropriate auto-start registration (Windows registry, Linux systemd/XDG autostart, macOS launchd)
4. THE IPC_Channel SHALL use localhost HTTP transport on Windows and Unix domain sockets on Linux and macOS
5. THE Background_Service SHALL store persistent data in platform-appropriate application data directories

### Requirement 11: UI Process On-Demand Startup

**User Story:** As a user, I want to open the UI quickly when needed and close it to free memory, so that I only use RAM for the UI when actively managing downloads.

#### Acceptance Criteria

1. WHEN the user clicks the system tray icon, THE Service_Lifecycle_Manager SHALL launch the UI_Process if it is not already running
2. WHEN the UI_Process starts, THE UI_Process SHALL connect to the Background_Service via IPC_Channel and display current download state within 3 seconds
3. WHEN the user closes all UI windows, THE UI_Process SHALL exit and release its memory
4. WHILE the UI_Process is not running, THE Background_Service SHALL continue processing downloads and responding to browser extension requests
5. THE UI_Process SHALL display a system tray icon that indicates the Background_Service status (running, downloads active, error)

### Requirement 12: Graceful Migration Path

**User Story:** As an existing user, I want the transition to the two-process architecture to preserve my existing downloads and settings, so that I do not lose data when upgrading.

#### Acceptance Criteria

1. WHEN the application is upgraded from the single-process version, THE Background_Service SHALL migrate existing download lists, part progress, queue configurations, and settings to the new persistence format
2. THE Background_Service SHALL detect the presence of legacy single-process data files and perform migration on first startup
3. IF migration fails for any item, THEN THE Background_Service SHALL log the failure and continue migrating remaining items
4. WHEN migration completes, THE Background_Service SHALL create a backup of the original data files before removing them
