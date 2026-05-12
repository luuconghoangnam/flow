# Migration Execution Plan (IDM -> Flow)

Muc tieu: khong lam tung thay doi nho roi dung, ma dong theo block lon den khi dat parity chuc nang voi `D:\Repos\IDM`.

## Nguyen tac thuc thi
- Basing 100% tren logic source cu, khong mo rong feature moi truoc parity.
- Moi block phai co Definition of Done ro rang va test script xac nhan.
- Chi bao milestone khi xong block, khong bao lặt vặt.

## Block 1 - Queue/State Parity (uu tien cao nhat)
### Nguon tham chieu
- `downloader/core/.../queue/*`
- `downloader/core/.../DownloadManager.kt`
- `desktop/pages/queue/*`

### Scope
- Queue groups (Main + named queues)
- maxConcurrent per queue
- stopQueueOnEmpty behavior
- start/stop/pause/resume queue-level
- ordering mutation: move up/down, swap, requeue
- queue event semantics (start/stop/empty/fail)

### Definition of Done
- Co the tao/sua/xoa queue, khong vo Main queue
- Queue active/inactive dung semantics
- Mutation item dung theo queue, khong cheo queue
- Resume/retry khong pha queue order
- Queue events duoc log day du va quan sat duoc
 - Queue events co command query runtime (`queue.events`) de debug/verify

## Block 2 - Settings/Proxy/Per-host Parity
### Nguon tham chieu
- `shared/app/.../proxy/*`
- `shared/app/.../perhostsettings/*`
- `desktop/pages/settings/*`

### Scope
- Settings page chia section:
  - Appearance
  - DownloadEngine
  - BrowserIntegration
- Proxy editor day du:
  - mode direct/system/manual
  - host/port/auth
  - exclude URL patterns
- Per-host editor day du:
  - add/edit/remove rule
  - host wildcard matching
  - override thread/user-agent/auth

### Definition of Done
- Moi setting thay doi trong UI -> luu vao settings storage -> runtime ap dung
- Proxy/per-host anh huong request that
- Co migration-safe defaults

## Block 3 - Tray/Startup/Clipboard UX Parity
### Nguon tham chieu
- `desktop/ui/widget/Tray.kt`
- `shared/auto-start/*`
- `shared/app/.../ClipboardUtil*`

### Scope
- Tray menu: show/hide/quit, consistency voi setting `use_system_tray`
- Startup integration (Windows Run key), feedback UX ro rang
- Clipboard monitor co flow xac nhan Queue/Dismiss (khong auto queue mu)

### Definition of Done
- UX tray/startup/clipboard dung on dinh, khong deadlock event loop
- Clipboard UX khong tao job rac

## Block 4 - Browser Integration + Packaging Parity Release
### Nguon tham chieu
- `integration:server`
- `desktop/utils/native_messaging/*`
- `.github/workflows` release/install tu source cu

### Scope
- Native messaging contract complete
- Chrome/Edge/Firefox registration scripts
- Installer hardening:
  - copy binaries/resources
  - register/unregister host
  - startup hooks
  - uninstall cleanup

### Definition of Done
- E2E: extension -> host -> queue -> download -> UI status hoat dong tren ban cai dat
- Co script test nhanh cho setup/recovery/uninstall

## Cach thuc thi tiep theo
1. Dong xong Block 1 truoc khi lan sang Block 2.
2. Sau moi block: chay build + smoke test + cap nhat parity matrix.
3. Chi bat dau toi uu/nang cap sau khi 4 block parity da xanh.
