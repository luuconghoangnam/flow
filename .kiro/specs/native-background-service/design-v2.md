# Design V2: Go Service + WebView Overlay + Kotlin Full UI

## Kiến trúc tổng quan

```
┌─────────────────────────────────────────────────────────────────┐
│  GO SERVICE (flow-service.exe) — LUÔN CHẠY — 7-12MB            │
│                                                                 │
│  ┌──────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────┐ │
│  │ Tray Icon│  │ HTTP Server  │  │  Download  │  │  Queue   │ │
│  │ (app icon│  │ port 15151   │  │  Engine    │  │  Manager │ │
│  │  native) │  │ (extension)  │  │ (multipart)│  │          │ │
│  └──────────┘  └──────────────┘  └────────────┘  └──────────┘ │
│  ┌──────────────┐  ┌─────────────────────────────────────────┐ │
│  │ IPC Server   │  │ WebView Overlay (add-download dialog)   │ │
│  │ port 15152   │  │ HTML/CSS, ~10MB khi mở, instant close   │ │
│  │ (full UI)    │  │ Dùng OS WebView (WebView2/WebKit)       │ │
│  └──────────────┘  └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
         │                              │
         │ start/stop                   │ extension gọi /add
         ▼                              ▼
┌─────────────────────────┐    ┌─────────────────────────────────┐
│  KOTLIN UI (Flow.exe)   │    │  BROWSER EXTENSION (background.js)│
│  Chỉ khi user mở       │    │  Bắt link → POST localhost:15151 │
│  ~200MB, tự exit khi    │    └─────────────────────────────────┘
│  đóng window            │
└─────────────────────────┘
```

## Flow chi tiết

### 1. Khởi động hệ thống (boot)
```
User login → OS auto-start flow-service.exe
  → Acquire single-instance lock
  → Start download engine (load pending downloads)
  → Start HTTP server (port 15151, extension)
  → Start IPC server (port 15152, full UI)
  → Show tray icon (app icon, native API)
  → RAM: 7MB
```

### 2. User mở app (click tray hoặc shortcut)
```
User click tray icon / double-click desktop shortcut
  → flow-service start Flow.exe
  → Flow.exe connect IPC (port 15152)
  → Hiện full UI (download list, settings, etc.)
  → RAM: 7MB (service) + 200MB (UI) = 207MB total
```

### 3. User đóng app (bấm X)
```
User bấm X trên window
  → Flow.exe exit process (giải phóng 200MB)
  → flow-service vẫn chạy (7MB)
  → Tray icon vẫn hiện
```

### 4. Extension bắt link (khi UI đóng)
```
User click download link trong browser
  → Extension POST http://localhost:15151/add
  → Go service nhận request
  → Mở WebView overlay (add-download dialog)
  → User bấm "Download" → service bắt đầu tải
  → Overlay tự đóng
  → RAM peak: 7MB + 10MB (WebView) = 17MB
```

### 5. Extension bắt link (khi UI đang mở)
```
User click download link trong browser
  → Extension POST http://localhost:15151/add
  → Go service nhận → forward đến Flow.exe qua IPC
  → Flow.exe hiện add-download dialog (Compose)
  → User bấm "Download" → Flow.exe gọi service tải
```

### 6. User exit hoàn toàn (right-click tray → Exit)
```
User right-click tray → Exit
  → Stop all active downloads (persist progress)
  → Stop HTTP/IPC servers
  → Kill Flow.exe nếu đang chạy
  → Remove tray icon
  → Release lock
  → Exit process
```

## Tray Icon

### Windows
- Win32 `Shell_NotifyIconW` API
- Load icon từ file `app_icon.ico` (bundled cùng binary)
- Left-click: mở full UI (start Flow.exe)
- Right-click menu: Show Downloads | Settings | Exit

### Linux
- libappindicator / StatusNotifierItem (D-Bus)
- Hoặc fallback: XEmbed tray (legacy)
- Icon: PNG file

### macOS
- NSStatusItem (Cocoa via cgo)
- Icon: template image (monochrome)

## WebView Overlay Dialog

### Công nghệ
- Windows: WebView2 (Edge Chromium, có sẵn Win10/11)
- Linux: WebKitGTK (có sẵn trên GNOME/KDE)
- macOS: WKWebView (built-in)

### Go library
- `github.com/nicholasgasior/gowv` hoặc `github.com/nicholasgasior/gowv`
- Hoặc `github.com/nicholasgasior/gowv`
- Recommend: `github.com/nicholasgasior/gowv` hoặc fork của `webview/webview`

### UI (HTML/CSS)
```html
<!-- Một file HTML duy nhất, embed trong Go binary -->
<div class="dialog">
  <h3>📥 Add Download</h3>
  <div class="field">
    <label>URL</label>
    <input id="url" readonly value="https://example.com/file.zip"/>
  </div>
  <div class="field">
    <label>Filename</label>
    <input id="name" value="file.zip"/>
  </div>
  <div class="field">
    <label>Save to</label>
    <input id="folder" value="C:\Users\Downloads"/>
    <button onclick="browse()">📁</button>
  </div>
  <div class="actions">
    <button class="primary" onclick="download()">Download</button>
    <button onclick="cancel()">Cancel</button>
  </div>
</div>
```

### JS ↔ Go bridge
```
window.flow.download(url, name, folder)  → Go: startDownload()
window.flow.browse()                     → Go: openFolderDialog() → return path
window.flow.cancel()                     → Go: closeWindow()
```

## File Structure

```
service/
├── main.go                 ← Entry point + tray
├── server.go               ← HTTP server (extension + IPC)
├── downloader.go           ← Multi-part download engine
├── queue.go                ← Queue management
├── storage.go              ← JSON persistence
├── config.go               ← Configuration
├── lock.go                 ← Single instance
├── tray_windows.go         ← Win32 tray (with app icon)
├── tray_linux.go           ← AppIndicator tray
├── tray_darwin.go          ← NSStatusItem tray
├── overlay.go              ← WebView overlay controller
├── overlay_windows.go      ← WebView2 binding
├── overlay_linux.go        ← WebKitGTK binding
├── overlay_darwin.go       ← WKWebView binding
├── ui/
│   ├── add_download.html   ← Overlay dialog HTML
│   ├── style.css           ← Dark theme CSS
│   └── bridge.js           ← JS ↔ Go communication
├── assets/
│   ├── icon.ico            ← Windows tray icon
│   ├── icon.png            ← Linux tray icon
│   └── icon_template.png   ← macOS template icon
└── go.mod
```

## Android

Giữ nguyên Kotlin/Compose. Không áp dụng Go service vì:
- Android đã có foreground service mechanism
- Không cần tray icon (notification thay thế)
- Download engine chạy trong service Android native
- Không có browser extension integration (dùng share intent)

## Implementation Tasks

### Sprint 1: WebView overlay (Windows first)
- [ ] Thêm dependency `webview/webview` vào Go service
- [ ] Tạo `overlay.go` - controller mở/đóng WebView
- [ ] Tạo `ui/add_download.html` - dialog UI
- [ ] Tạo `ui/style.css` - dark theme matching app
- [ ] Implement JS ↔ Go bridge (download, browse, cancel)
- [ ] Sửa `/add` handler: mở overlay thay vì launch Flow.exe
- [ ] Test: extension bắt link → overlay hiện → download starts

### Sprint 2: Tray icon với app icon
- [ ] Bundle `icon.ico` vào Go binary (embed)
- [ ] Load custom icon trong tray (thay vì IDI_APPLICATION)
- [ ] Right-click menu: Show Downloads | Settings | Exit
- [ ] Left-click: launch Flow.exe

### Sprint 3: IPC integration (service ↔ full UI)
- [ ] Khi Flow.exe mở: connect IPC, nhận download list từ service
- [ ] Khi extension gọi /add VÀ Flow.exe đang mở: forward đến UI
- [ ] Khi Flow.exe đóng: service tiếp tục tải, hiện overlay cho link mới

### Sprint 4: Linux + macOS tray & overlay
- [ ] Linux: AppIndicator tray
- [ ] macOS: NSStatusItem tray
- [ ] Linux: WebKitGTK overlay
- [ ] macOS: WKWebView overlay
- [ ] Cross-compile test

### Sprint 5: Packaging & installer
- [ ] Windows: NSIS installer (service + UI + extension manifest)
- [ ] Linux: .deb package (systemd user service)
- [ ] macOS: .dmg (launchd agent)
- [ ] Auto-start registration per platform
- [ ] Update GitHub Actions CI

## RAM Budget

| State | Target | Components |
|-------|--------|------------|
| Idle (tray only) | ≤10MB | Go service + tray |
| Overlay open | ≤20MB | + WebView dialog |
| Full UI open | ≤220MB | + Kotlin/Compose |
| Downloading (no UI) | ≤15MB | Go service + active connections |
