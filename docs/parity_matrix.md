# Feature Parity Matrix: IDM -> Flow

Nguon tham chieu: `D:\Repos\IDM`
Muc tieu: chuyen doi sang Rust/Slint trong `D:\Repos\Flow` voi du chuc nang desktop chinh.

## 1) Kien truc tong the

| IDM module | Vai tro | Flow module dich | Trang thai |
|---|---|---|---|
| `downloader:core`, `downloader:monitor` | Engine tai xuong, giam sat toc do/trang thai | `app/flow-core` | Dang khoi tao |
| `integration:server` + `desktop/.../native_messaging` | Browser integration / native messaging | `app/flow-messaging` | Co framing, chua E2E |
| `desktop:app` + `shared:app` | Desktop UI + business app layer | `app/flow-ui` + `app/flow-core` | UI shell moi |
| `shared:config` | Config/settings datastore | `app/flow-core` (storage + settings module sap them) | Chua port |
| `shared:auto-start` | Auto start theo OS | `app/flow-ui`/installer hooks | Chua port |
| `shared:updater` | Auto update | Chua tao crate rieng | Chua port |

## 2) Block trien khai uu tien (bao quat -> chi tiet)

### Block A - Download Core parity (uu tien cao nhat)
- **IDM tham chieu**: `downloader:core`, `downloader:monitor`, cac action xu ly download trong `desktop/app`.
- **Flow dich**: `app/flow-core/src/download.rs`, `app/flow-core/src/model.rs`.
- **Da co**:
  - `probe()` HEAD metadata
  - single stream download + resume co ban theo `Range`
  - `ChunkPlan` scaffold
  - event channel `DownloadEvent`
  - multi-connection baseline + part merge pipeline
  - state machine scaffold (`Start/Pause/Resume/Cancel/Retry`)
  - chunk metadata table (`download_chunks`) for resume persistence
  - chunk progress event de dong bo persistence theo thoi gian thuc
  - resume hook cho multi-connection theo persisted chunk offsets
  - orchestrator hook doc chunk progress tu repository truoc khi resume
  - debounce persistence worker cho `ChunkProgress` events -> SQLite
  - verify output size + optional SHA256 sau merge
  - control failure events (`Cancelled`/`Failed`) ro rang
  - verify tat ca part sizes truoc khi merge
  - port logic tu IDM: split range co `minPartSize` va gioi han max part count
  - port logic tu IDM: duplicate output file dung numbered suffix `_1`, `_2`, ...
  - port logic integration: filename fallback tu URL thay vi `download.bin`
  - port logic credentials: headers/referrer/cookies/user-agent tu IDM `HttpDownloadCredentials`
  - port logic credentials: Basic Auth username/password -> Authorization Basic
  - port logic validation: chunk request bat buoc HTTP 206 va validate Content-Length cua range
  - port logic response: parse `Content-Disposition` filename / filename*
  - port logic validation: parse va validate `Content-Range` start/end cho chunk response
  - port schema settings tu IDM: `direct/system/manual proxy`, exclude URL patterns, per-host username/password/user-agent/thread-count
  - apply manual proxy vao reqwest client (HTTP/SOCKS URL form) va persist proxy fields trong queue job
- **Con thieu de parity**:
  - chunk parallel that su (8/16/32)
  - merge `.part` + checksum verify
  - pause/resume/cancel state machine
  - retry/backoff + host failure policy
  - rate limit token bucket
- **Definition of Done**:
  - tai file lon bang multi-connection nhanh hon single-stream
  - pause/resume sau restart van tiep tuc dung byte offset
  - khong hong file khi mat mang/dung app dot ngot

### Block B - Queue + Storage parity
- **IDM tham chieu**: `desktop/.../pages/queue/*`, `shared/app/.../onqueuecompletion/*`, `desktop/.../storage/*`.
- **Flow dich**: `app/flow-core/src/storage.rs` + module queue moi.
- **Da co**:
  - SQLite WAL + bang downloads co ban
  - `queue_jobs` table cho job state va restart recovery
  - `QueueScheduler` baseline voi max concurrent downloads
  - `list_recoverable_jobs()` de load job chua xong sau restart
  - app startup recovery nap job vao queue worker
  - native messaging enqueue job vao DB + queue worker, khong block download trong callback
  - persistence worker cap nhat queue job status theo `DownloadEvent`
  - queue worker dung repository-aware resume theo chunk metadata
  - retry policy cap job trong queue worker
  - retry metadata (`attempt_count`, `last_error`) trong `queue_jobs`
  - query API `get_queue_job` / `list_queue_jobs` cho UI/extension status
- **Con thieu**:
  - queue table, chunk table, retry metadata
  - scheduler (max active downloads, priority)
  - on completion actions (open file, shutdown, custom)
- **Definition of Done**:
  - queue khong mat trang thai qua restart
  - enforce max concurrent tasks on all platforms

### Block C - Native Messaging parity (Extension <-> App)
- **IDM tham chieu**: `desktop/.../utils/native_messaging/*`, `integration:server`.
- **Flow dich**: `app/flow-messaging/src/lib.rs` + host binary (se tao).
- **Da co**:
  - 4-byte little-endian framing
  - envelope version `ExtensionEnvelope<T>`
  - command routing `download.create`, `download.status`, `download.list`
  - response schema `HostAck` voi `status_code`, `message`, `download_id`, `data`
  - status/list commands ket noi SQLite queue query API
  - extension bo qua `blob:`, `data:`, `filesystem:` va chi gui `http(s)` downloads
  - host reject non-http(s) bang ACK `400`
  - commands `download.retry` va `download.cleanup` de quan ly queue cu/loi
  - commands `download.start` / `download.stop` cho queued/paused jobs
  - browser extension dung command contract moi
  - Windows native host manifest + register/unregister scripts cho Chrome/Edge
  - tach Native Messaging host thanh binary headless `flow-host.exe`
  - `flow-ui` khong con chay stdio host loop
- **Con thieu**:
  - command schema day du (create/pause/resume/list/status)
  - host loop stdin/stdout running mode
  - manifest register scripts cho Chrome/Edge/Firefox
- **Definition of Done**:
  - extension gui URL + header + cookies, app nhan va enqueue thanh cong
  - restart browser/app van reconnect on demand

### Block D - UI parity (Slint)
- **IDM tham chieu**: `desktop/.../pages/home/*`, `.../addDownload/*`, `.../settings/*`, `.../queue/*`, `.../widget/Tray.kt`.
- **Flow dich**: `app/flow-ui/ui/*.slint`, `app/flow-ui/src/main.rs`.
- **Da co**:
  - main window shell + list mock
  - home list doc queue jobs that tu SQLite thay vi mock tinh
  - live refresh list theo polling DB
  - event-driven refresh qua signal file `queue.signal` tu host -> UI watcher
  - UI actions: retry failed jobs, cleanup completed/failed/cancelled jobs
  - list item hien thi status + progress + attempt + last error
  - queue channel sap xep batch theo `priority` truoc khi spawn job
  - queue recovery sau restart chi auto-load `Queued`/`Downloading`; `Paused`/`Failed` doi `start`/`retry`
  - active worker bo qua/thoat som neu DB status chuyen `Paused` hoac `Cancelled`
  - queue order persisted (`queue_order`) de port dan semantics `move/requeue/resume position`
  - queue_groups table + default `Main` queue groundwork de port `QueueManager`/named queues
  - queue_groups co `max_concurrent` va `stop_on_empty` groundwork
  - scheduler tao semaphore theo `queue_id` / queue group thay vi global single limit
  - host commands `queue.list/create/update/delete`
  - UI co queue chips + create/toggle/delete queue controls toi thieu
  - UI co item controls toi thieu: move up/down, requeue, maxConcurrent+/-, toggle stop_on_empty
  - queue config panel toi thieu hien summary queue duoc chon
  - settings storage hop nhat `%LOCALAPPDATA%\\Flow\\settings.json`
  - bootstrap Windows auto-start qua registry `Run` khi setting bat
  - clipboard monitoring toi thieu trong host khi setting bat
  - clipboard pending confirm flow (Queue/Dismiss) giua host va UI
  - settings summary/toggles trong UI cho auto-start, browser integration, clipboard monitoring
  - UI toggle auto-start ap registry ngay; them `use_system_tray` setting/toggle groundwork
  - settings summary hien trang thai startup registry thuc te (Run key) de UX ro rang hon
  - settings controls toi thieu cho thread count / max concurrent / default folder / proxy mode summary
  - proxy/per-host controls toi thieu trong UI (proxy host/port shortcuts, sample per-host rule, clear rules)
  - per-host/proxy editor day hon: auth sample/clear, remove-last host rule
  - settings chia thanh summary sections theo huong IDM: Appearance / Download Engine / Browser Integration
  - `download.create/start` co the mang `queue_id` de dua job vao named queue
  - host commands `queue.move` / `queue.requeue`
  - `stop_on_empty` bat dau deactivate queue group khi queue het job runnable
  - toggle inactive queue trong UI se pause active jobs; bat lai queue se chuyen `Paused` -> `Queued`
  - queue runtime event log table (`queue_events`) cho semantics start/stop/empty/failed
- **Con thieu**:
  - home list binding tu core events
  - add download dialog, settings, queue page
  - tray/single instance/background behavior
  - clipboard monitor popup
- **Definition of Done**:
  - UI khong freeze khi tai file lon
  - co full luong nguoi dung: add -> monitor -> pause/resume -> complete

### Block E - Packaging/Installer parity
- **IDM tham chieu**: setup logic + CI publish workflows trong `.github/workflows` va desktop integration utils.
- **Flow dich**: `installer/flow.iss` + scripts register host.
- **Con thieu**:
  - installer script copy binary/resources
  - native messaging registration per browser
  - uninstall cleanup + startup toggle
- **Da co**:
  - `flow-host --health` va `flow-host --version`
  - Inno Setup skeleton `installer/flow.iss`
  - installer copy `flow-ui.exe`, `flow-host.exe`, extension va native messaging scripts
  - SQLite bundled trong Rust build de tranh thieu `sqlite3.lib` tren Windows
- **Definition of Done**:
  - 1-click installer, cai xong extension ket noi duoc ngay

## 3) Lo trinh chi tiet de dat parity

1. Hoan tat Block A truoc (core engine)
2. Sang Block B (queue + persistence) de core on dinh
3. Hoan tat Block C de co pipeline browser -> app
4. Port Block D theo tung man hinh uu tien: Home -> Add -> Queue -> Settings
5. Chot Block E va hardening release

## 4) Rule implementation

- Moi block phai co benchmark/checklist pass-fail ro rang
- Uu tien parity behavior truoc, toi uu giao dien sau
- Khong mo rong feature moi truoc khi parity baseline da xanh
