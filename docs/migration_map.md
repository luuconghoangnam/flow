# Migration Map: IDM (Kotlin/Compose) -> Flow (Rust/Slint)

## Scope analyzed
- Legacy source: `D:\Repos\IDM`
- New source: `D:\Repos\Flow`

## Module mapping
- `desktop/app/.../utils/native_messaging/*` -> `app/flow-messaging/src/lib.rs`
- `shared/app` domain models + queue state -> `app/flow-core/src/model.rs`
- download + queue orchestration logic -> `app/flow-core/src/download.rs`
- settings/history persistence -> `app/flow-core/src/storage.rs`
- Compose desktop UI (`desktop/app/.../ui/*`) -> Slint UI `app/flow-ui/ui/main.slint`

## Implemented baseline in Flow
- Workspace already split into `flow-core`, `flow-messaging`, `flow-ui` crates.
- Added core domain model for download task lifecycle.
- Added repository abstraction and SQLite WAL bootstrap.
- Added Native Messaging framing (4-byte little-endian length prefix).
- Replaced placeholder UI with a real Slint window and download list layout.

## Next migration steps
1. Port concrete download engine from placeholder `enqueue()` to reqwest range-chunk downloader.
2. Add event bus (tokio mpsc/broadcast) between core and UI to stream progress in real time.
3. Add browser extension handshake JSON schema versioning.
4. Add installer scripts for host registration (Windows registry, Linux/macOS manifest paths).
