# Báo cáo Khảo sát & Đề xuất Cải thiện — Flow Download Manager

> **Repo**: `flowspeed.link` · **Ngày khảo sát**: 2026-07-05
> **Quy mô**: ~91.789 dòng Kotlin, 963 file `.kt`, 16 Gradle modules + 5 composite build modules + 7 convention plugins
> **Stack**: Kotlin Multiplatform + Jetpack Compose (Desktop + Android) + Decompose + Koin + Ktor/OkHttp + DataStore
> **Indexer**: GitNexus (13.859 symbols, 47.710 relationships, 300 execution flows)

---

## Mục lục

1. [Tổng quan kiến trúc hiện trạng](#1-tổng-quan-kiến-trúc-hiện-trạng)
2. [Cải thiện Hiệu năng](#2-cải-thiện-hiệu-năng)
3. [Cải thiện Giao diện](#3-cải-thiện-giao-diện)
4. [Cải thiện Kết nối giữa các Module](#4-cải-thiện-kết-nối-giữa-các-module)
5. [Cải thiện Tách biệt Module](#5-cải-thiện-tách-biệt-module)
6. [Yêu cầu chuẩn cho ứng dụng KMP phức tạp](#6-yêu-cầu-chuẩn-cho-ứng-dụng-kmp-phức-tạp)
7. [Đánh giá phản biện — Đề xuất nào thực sự có giá trị?](#7-đánh-giá-phản-biện--đề-xuất-nào-thực-sự-có-giá-trị)
8. [Bảng ưu tiên hành động (đã điều chỉnh)](#8-bảng-ưu-tiên-hành-động-đã-điều-chỉnh-sau-deep-review)

---

## 1. Tổng quan kiến trúc hiện trạng

### 1.1. Module graph

| # | Module | Loại | Mục đích |
|---|---|---|---|
| 1 | `android:app` | Android Application | Entry point Android |
| 2 | `desktop:app` | JVM/Compose Desktop | Entry point Desktop |
| 3 | `desktop:app-utils` | JVM | Helper dùng JBR cho desktop |
| 4 | `desktop:shared` | JVM/Compose Desktop | Shared desktop-specific (JNA) |
| 5 | `desktop:mac-utils` | JVM | macOS event handling (Dock click, About, Quit) |
| 6 | `downloader:core` | KMP (JVM+Android) | Engine download HTTP/HLS, parallel streams |
| 7 | `downloader:monitor` | KMP (JVM+Android) | Theo dõi trạng thái downloads |
| 8 | `integration:server` | JVM-only | HTTP server cho browser extension |
| 9 | `shared:utils` | KMP | OkHttp, Okio, semver, Arrow, datetime, JNA |
| 10 | `shared:app` | KMP + Compose | Shared UI: pages, components, themes |
| 11 | `shared:compose-utils` | KMP + Compose | Compose helpers (pure) |
| 12 | `shared:resources` | KMP + Compose | i18n resource generator |
| 13 | `shared:resources:contracts` | KMP | Interface `MyStringResource` (zero-dep) |
| 14 | `shared:config` | JVM | DataStore config wrapper |
| 15 | `shared:updater` | KMP | Auto-update checker/applier |
| 16 | `shared:auto-start` | KMP | Auto-start on boot (registry/XDG/loginitems) |
| 17 | `shared:local-server` | JVM-only | Nano HTTP server + http4k |

**Composite Builds** (`compositeBuilds/`):
- `shared/platform` — KMP library `com.flowspeed.lib.util:platform:1` (chỉ JVM, OS/arch detection).
- `plugins/git-version-plugin` — plugin `com.flowspeed.lib.git-version-plugin` (JGit + semver).
- `plugins/installer-plugin` — plugin `com.flowspeed.lib.installer-plugin` (NSIS, DMG).
- `plugins/common-android` — plugin `com.flowspeed.lib.common-android` (Manifest XML generator, APK signer).

**Convention Plugins** (`buildSrc/src/main/kotlin/myPlugins/`):
- `kotlin.gradle.kts` — `kotlin("jvm")` + opt-ins + feature `context-parameters`.
- `kotlinAndroid.gradle.kts` — `kotlin("android")` + same opt-ins.
- `kotlinMultiplatform.gradle.kts` — `kotlin("multiplatform")` + same opt-ins.
- `composeBase.gradle.kts` — apply `kotlin("plugin.compose")` + `org.jetbrains.compose`.
- `composeDesktop.gradle.kts` — `composeBase` + `kotlin` + `compose.desktop.currentOs` (loại bỏ Material).
- `composeAndroid.gradle.kts` — `composeBase` + `kotlinAndroid`.
- `proguardDesktop.gradle.kts` — resolve ProGuard rules từ jars trong classpath.

### 1.2. Sơ đồ phụ thuộc (rút gọn)

```
                          ┌──────────────────┐
                          │  android:app     │
                          │  desktop:app     │
                          └────────┬─────────┘
                                   │
              ┌────────────────────┼─────────────────────┐
              ▼                    ▼                     ▼
     ┌────────────────┐   ┌────────────────┐    ┌──────────────────┐
     │ desktop:shared │   │ desktop:app-   │    │ desktop:mac-utils│ (macOS events)
     │                │   │     utils      │    └──────────────────┘
     └────────┬───────┘   └────────┬───────┘
              │                    │
              ▼                    ▼
         ┌────────────────────────────────┐
         │       shared:app               │  ← "god module"
         │  (UI: pages, themes, MVIs)     │  8 api deps
         └──┬───────┬────────┬───────┬────┘
            │       │        │       │
            ▼       ▼        ▼       ▼
   downloader:  shared:   shared:   shared:
   core +       config,   compose-  resources,
   monitor      utils,    utils     auto-start,
                local-              updater
                server
            │
   ┌────────┴────────┐        ┌──────────────────┐
   ▼                 ▼        ▼                  ▼
shared:utils   integration:   downloader:core   shared:resources
   │              server           │              │
   │               │               │              │
   ▼               ▼               ▼              ▼
shared:local-  shared:utils    shared:utils   shared:resources:
   server                                    contracts
```

---

## 2. Cải thiện Hiệu năng

### 🔴 Nghiêm trọng

#### 2.1. `DownloadMonitor` scope — singleton by-design, nhưng thiếu explicit shutdown
**File**: `downloader/monitor/src/commonMain/kotlin/com/flowspeed/lib/downloader/monitor/DownloadMonitor.kt:28`

```kotlin
private val scope = CoroutineScope(SupervisorJob())
```

**Đánh giá lại**: `DownloadMonitor` được đăng ký là Koin `single` (xác nhận ở `DownloaderModule.kt`). Nó sống toàn bộ vòng đời app → `CoroutineScope(SupervisorJob())` là **hợp lý cho singleton**. Khi process chết, scope cũng chết.

**Thêm vào đó**: Pattern `subscriptionCount` → start/stop jobs là thiết kế **thông minh** — tiết kiệm CPU khi không có subscriber nào.

**Rủi ro thật sự**:
- Trong unit test: nếu tạo nhiều `DownloadMonitor` instance, scope sẽ leak giữa tests.
- Trên Android: nếu tương lai muốn support multi-user / work profile, singleton assumption có thể sai.

**Đề xuất nhẹ** (không P0, nên P2):
- Implement `Closeable` interface cho testability.
- Không cần bind lifecycle hiện tại — singleton scope là đúng cho download manager app.

#### 2.2. ~~`HomePage` KHÔNG dùng Lazy list~~ — **SAI: Cả hai platform đều dùng LazyColumn**
**File**: `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/pages/home/sections/DownloadList.kt` và `android/app/src/main/kotlin/com/flowspeed/link/android/pages/home/DownloadList.kt`

**Thực tế đã verified**:
- **Desktop**: Dùng custom `Table` composable (`shared/app/src/desktopMain/.../widget/table/customtable/Table.kt:159`) bên trong wrap `LazyColumn` với `key = { it.id }`.
- **Android**: Dùng `LazyColumn` trực tiếp ở `DownloadList.kt:59` với `itemsIndexed(items = downloadList, key = { _, item -> item.id })` + `Modifier.animateItem()`.
- **Android CardGrid**: Cũng dùng `LazyVerticalGrid` ở `CardGrid.kt:36`.

→ **Đề xuất này KHÔNG CẦN THIẾT** — cả hai platform đã xử lý đúng. `Column` ở `HomePage.kt` chỉ là layout wrapper bao ngoài, không phải list renderer.

> ⚠️ Lưu ý: Mục 2.2 ban đầu sai — nhầm lẫn giữa `Column` layout wrapper với list rendering logic.

#### 2.3. `intervalFlow(500ms)` — hợp lý cho use case, nhưng có thể tối ưu hơn
**File**: `DownloadMonitor.kt:254-258`

```kotlin
private fun startUpdateActiveDownloadList() {
    downloadListUpdaterJob = merge(
        downloadManager.listOfJobsEvents.map { },
        downloadSpeedFlow,
        headlessQueuePendingItemsFlow,
        intervalFlow(500)        // ← Poll 500ms
    ).onEach { ... }.launchIn(scope)
}
```

**Đánh giá lại**:
- `downloadJobs` chỉ chứa **non-completed downloads** (tạo từ pending items khi boot). Với `maxConcurrentDownloads` default = 0 (unlimited), thực tế user hiếm khi có >50 active jobs cùng lúc.
- `intervalFlow(500)` đóng vai trò **safety net** — đảm bảo UI refresh ít nhất mỗi 500ms ngay cả khi events bị miss.
- Đã có `downloadManager.listOfJobsEvents` và `downloadSpeedFlow` emit event-driven → poll chỉ là backup.
- Chi phí thực tế: iterate list ~10-50 items + đọc `status.value` (in-memory StateFlow) → **rất nhẹ**, không I/O.

**Kết luận**: Không phải performance issue thực sự. Pattern merge(events + interval) là common trong reactive UI.

**Đề xuất nhẹ** (P2, chỉ nếu muốn tiết kiệm pin mobile):
- Tăng interval lên 1000ms khi app ở background.
- Giữ 500ms khi app foreground — người dùng kỳ vọng UI responsive.

#### 2.4. `HttpDownloadJob` 775 dòng — quá lớn
**File**: `downloader/core/src/commonMain/kotlin/com/flowspeed/lib/downloader/downloaditem/http/HttpDownloadJob.kt`

Chứa: part splitting + retry logic + ETag validation + web page detection + cancel logic. Khó maintain, khó test, nhiều bug potential.

**Đề xuất tách**:
- `HttpPartSplitting` — quyết định số chunks, kích thước.
- `HttpRetryPolicy` — backoff, max retry, error classification.
- `HttpResponseValidator` — ETag, Last-Modified, web page detection.
- `HttpDownloadJob` (slim) — orchestrator gọi các module trên.

### 🟡 Trung bình

#### 2.5. `DownloadListFileStorage.getAll()` đọc tuần tự từng file
**File**: `downloader/core/src/commonMain/kotlin/com/flowspeed/lib/downloader/db/DownloadListFileStorage.kt:22-34`

```kotlin
override suspend fun getAll(): List<IDownloadItem> {
    return withContext(Dispatchers.IO) {
        val jsonExtension = ".json"
        downloadListFolder.listFiles()
            ?.mapNotNull { file ->
                file.name
                    .takeIf { it.endsWith(jsonExtension) }
                    ?.removeSuffix(jsonExtension)
                    ?.toLongOrNull()
                    ?.let { get(file, it) }
            }.orEmpty()
    }
}
```

→ O(n) I/O mỗi query. Với 1000 downloads cần đọc 1000 file JSON.

**Trade-off hợp lý**: 1 file hỏng → mất 1 item, không mất toàn bộ DB (đúng cho download manager).

**Đề xuất cải thiện**:
- Cache kết quả `getAll()` trong memory, invalidate khi có write.
- Background warm-up khi app start.
- Optional: index file (chỉ metadata, không full data).

#### 2.6. `AppComponent` 1.221 dòng — god component
**File**: `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/AppComponent.kt`

Chứa 12+ slot navigation:
- Home (line 195-218)
- Queues (line 224-236)
- BatchDownload (line 240-266)
- EditDownload (line 268-298)
- Settings (line 334-345)
- AddDownload (line 350-450) — PagesNavigation + childPages
- DownloadDialog (line 456-482)
- CategoryDialog (line 490-522)
- FileChecksum (line 885-901)
- EnterNewURL (line 1040-1066)
- PowerAction (line 1111-1128)
- PerHostSettings (line 1157-1173)

**Đề xuất tách**:
- `RootAppComponent` — theme, locale, system tray.
- `HomeRootComponent` — Home + Settings.
- `DialogsRootComponent` — AddDownload / EditDownload / DownloadDialog / CategoryDialog / FileChecksum.
- `UpdatesRootComponent` — Update + PerHostSettings.
- `AppActionsComponent` — system integration (notify, beep, exit).

#### 2.7. `runBlocking` trong Koin module init
**File**: `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/di/UiModule.kt:70-77`

```kotlin
single {
    val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
    val context = DefaultComponentContext(lifecycle)
    runBlocking {
        withContext(Dispatchers.Main) {
            AppComponent(context)
        }
    }
}
```

→ `runBlocking` block main thread khi khởi tạo `AppComponent`.

**Đề xuất**:
- Dùng `CoroutineStart.UNDISPATCHED` hoặc lazy init `by lazy`.
- Hoặc inject `AppComponent` qua Factory + delegate creation.

### ✅ Điểm tốt giữ lại

- **Subscription-count-based start/stop**: `DownloadMonitor.kt:56-85` dùng pattern tốt — start background jobs chỉ khi có subscriber, stop khi unsubscribe.
- **Proxy client cache**: `OkHttpHttpDownloaderClient.kt:22` dùng `ConcurrentHashMap<ProxyStrategy, OkHttpClient>` → tránh recreate client.
- **HTTP/1.1 cố ý** ở `NetworkModule.kt:32`: đúng cho download manager (tránh HTTP/2 multiplexing head-of-line blocking).
- **JVM tuning** ở `desktop/app/build.gradle.kts:107-125`: `MaxHeapFreeRatio=20`, `Xmx192m`, `UseStringDeduplication` — best practice.

---

## 3. Cải thiện Giao diện

### 🔴 Quan trọng

#### 3.1. ~~Thiếu `LazyColumn` ở HomePage~~ — **ĐÃ XÁC NHẬN SAI**
Mục 2.2 đã bị bác bỏ — cả Desktop (custom `Table` wrapping `LazyColumn`) và Android (`LazyColumn` + `LazyVerticalGrid`) đều đã virtualized. Không có vấn đề UX ở đây.

#### 3.2. Accessibility (a11y) yếu
Chỉ 72 hit cho `contentDescription/semantics/onClick/focusable` (grep). Thiếu:
- `semantics { contentDescription = "..." }` rõ ràng cho icon-only buttons.
- `Modifier.semantics(mergeDescendants = true)` cho grouped controls.
- `focusOrder` cho keyboard navigation desktop.
- Test với TalkBack (Android) + screen reader (desktop).
- `LiveRegion` cho status updates (download progress).

**Đề xuất checklist khi viết composable**:
```kotlin
IconButton(onClick = ...) {
    Icon(
        imageVector = Icons.Default.Pause,
        contentDescription = stringResource(Res.string.pause_download)  // ← bắt buộc
    )
}
```

#### 3.3. `derivedStateOf` ít dùng — missing recomposition optimization
- `remember` được dùng 192 lần — OK.
- `derivedStateOf` & `key()` rất ít xuất hiện → nhiều recomposition không cần thiết.

**Đề xuất**:
- Audit các composable có logic tính toán (filter, sort, count) → bọc trong `derivedStateOf`.
- Dùng `key(item.id)` trong `items()` để tránh recompose toàn list.

### 🟡 Tốt nhưng cần cải thiện

#### 3.4. 6 themes (không phải 5 như README)
**File**: `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/theme/DefaultThemes.kt:155-164`

1. `dark` — "Cyber Dark"
2. `light` — "Cyber Light"
3. `obsidian` — "Terminal"
4. `deepOcean` — "Deep Ocean"
5. `black` — "OLED Black"
6. `lightGray` — "Industrial"

Design system mạnh, custom (không Material3) → độc lập nhưng tốn effort maintain. Thiếu:
- Theme marketplace / user customization.
- Per-theme component overrides.
- Sửa README cho khớp (đang nói "5 themes").

#### 3.5. Animation & state transition
- `rememberInfiniteTransition` dùng cho shimmer ở `DownloadCard.kt:93` — OK.
- **Thiếu**: transition giữa các state Downloading → Completed → Failed (hiện chỉ đổi text/icon).

#### 3.6. i18n hạn chế
**File**: `shared/resources/src/commonMain/resources/com/flowspeed/link/resources/locales/`

Chỉ 2 locale: `en_US.properties`, `vi_VN.properties`. Generator tốt (`PropertiesToKotlinTask` ở `shared/resources/build.gradle.kts:135-241`) nhưng:
- README không đề cập roadmap i18n.
- `buildConfigField("PROJECT_TRANSLATIONS", "")` ở `shared/app/build.gradle.kts:132-137` để trống → link tới Weblate/Crowdin chưa setup.

**Đề xuất**:
- Thêm zh_CN, ja, ko, de, fr, es.
- Setup Crowdin/POEditor cho community contribution.

### ✅ Điểm tốt giữ lại

- **Custom design system** không Material → độc lập, branding rõ ràng.
- **JVM tuning** cho desktop memory: heap tối đa 192MB, G1GC aggressive.

---

## 4. Cải thiện Kết nối giữa các Module

### 🔴 Nghiêm trọng

#### 4.1. `shared:app` là "god module" — `api()` 8 module khác
**File**: `shared/app/build.gradle.kts:28-36`

```kotlin
api(project(":downloader:core"))
api(project(":downloader:monitor"))
api(project(":shared:config"))
api(project(":shared:utils"))
api(project(":shared:compose-utils"))
api(project(":shared:resources"))
api(project(":shared:auto-start"))
api(project(":shared:updater"))
```

Khi `desktop:app` depend `shared:app`, nó kéo theo tất cả 8 module. Không thể dùng `shared:resources` mà không kéo theo `downloader:core`. **Phá vỡ module isolation**.

**Đề xuất**:
- Chuyển `api()` → `implementation()` cho các module không cần re-export từ public API của `shared:app`.
- `api()` chỉ giữ cho: `:shared:resources` (cần cho `stringResource()`), `:shared:compose-utils` (Compose dependencies).

#### 4.2. Android `Di.kt` 640 dòng vs Desktop 10 file module — inconsistency lớn

**Desktop** — `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/di/`: 10 file riêng biệt:
- `DownloaderModule.kt` (116 dòng)
- `DownloadSystemModule.kt` (87 dòng)
- `IntegrationModule.kt`
- `NetworkModule.kt` (52 dòng)
- `PlatformModule.kt`
- `SerializationModule.kt`
- `StorageModule.kt`
- `UiModule.kt` (91 dòng)
- `UpdaterModule.kt`
- `Di.kt` (composition root)

**Android** — `android/app/src/main/kotlin/com/flowspeed/link/android/di/Di.kt`: 1 file 640 dòng, chứa 7 modules inline + 30+ `single`.

Đặc biệt: `downloadSystemModule` (Android line 249-352) **copy-paste 90% từ desktop DownloadSystemModule.kt**.

→ Nếu developer sửa logic ở desktop (vd thêm proxy mới), Android sẽ không update → **bug không đồng bộ**.

**Đề xuất**:
- Move các Koin module definitions ra `shared:app` (hoặc `shared:di`).
- Tái cấu trúc Android theo pattern Desktop (10 file module nhỏ).

#### 4.3. `downloader:monitor` LEAK Compose
**File**: `downloader/monitor/src/commonMain/kotlin/com/flowspeed/lib/downloader/monitor/UiPart.kt:3`

```kotlin
package com.flowspeed.lib.downloader.monitor

import androidx.compose.runtime.Immutable          // ← LEAK
```

Module tên "monitor" nhưng:
- File `UiPart.kt` chỉ chứa UI model classes (UiRangedPart, UiDurationBasedPart).
- `build.gradle.kts:6` apply `myPlugins.composeBase` → phụ thuộc Compose Compiler.

**Đề xuất**:
- Đổi tên module: `downloader:monitor-ui`.
- Hoặc tách `UiPart` ra module riêng trong `shared:app`.
- `downloader:monitor` chỉ chứa pure state (không Compose).

### 🟡 Trung bình

#### 4.4. `shared:config` — JVM-only, cần cân nhắc KMP hóa
**File**: `shared/config/build.gradle.kts` + `shared/config/src/main/kotlin/com/flowspeed/lib/util/config/`

- `shared:app` khai báo `api(project(":shared:config"))` → module đang được dùng tích cực.
- Import `com.flowspeed.lib.util.config.*` xuất hiện ở 9+ file: `StorageModule.kt`, `AppSettingsStorage.kt` (cả Desktop + Android), `HomePersistedState.kt`, `BaseSettings.kt`, v.v.
- JVM-only (chỉ `id(MyPlugins.kotlin)`), không KMP.
- 8 file: `Config.kt`, `ConfigKeyWithPrimitiveType.kt`, `JsonMapper.kt`, `NestedCreator.kt`, `extensions.kt`, `datastore/KotlinSerializationDataStore.kt`, `datastore/MapConfigDataStore.kt`, `ConfigToJson.kt`.

**Đề xuất**:
- Nếu muốn Android cũng dùng trực tiếp (không qua `shared:app`) → KMP hóa module.
- Gộp vào `shared:utils` nếu muốn giảm module count (cả hai đã cùng JVM).

#### 4.5. `desktop:mac-utils` — module nhỏ, cân nhắc gộp
**File**: `desktop/mac-utils/src/main/kotlin/com/flowspeed/lib/util/desktop/mac/event/MacEventHandler.kt`

- Chứa 1 file duy nhất: `MacEventHandler.kt` (42 dòng) — xử lý macOS Dock icon click, About, Preferences, Quit events qua `java.awt.Desktop`.
- Module rất nhỏ, tồn tại riêng biệt có lẽ để tách platform-specific code.

**Đề xuất**:
- Giữ lại nếu muốn isolation rõ ràng cho macOS-only code.
- Hoặc gộp vào `desktop:app-utils` để giảm module count (cả hai đều JVM-only, desktop-specific).

#### 4.6. Convention plugins — shared body, cân nhắc DRY
**File**: `buildSrc/src/main/kotlin/myPlugins/`

- `kotlin.gradle.kts`
- `kotlinAndroid.gradle.kts`
- `kotlinMultiplatform.gradle.kts`

Cả 3 có body giống nhau (`getOptIns()`, `getFeatures()`, `freeCompilerArgs`) — chỉ khác `plugins {}` block. Đây là pattern phổ biến trong convention plugins, nhưng violate DRY khi cần thêm opt-in mới phải sửa 3 chỗ.

**Đề xuất**:
- Tạo file utility `KotlinCompilerConfig.kt` chứa opt-ins/features, 3 plugin gọi chung.
- Hoặc dùng `allprojects {}` / precompiled script plugin cha để tránh lặp.

### ✅ Điểm tốt giữ lại

- **Decompose**: chọn đúng cho KMP đa nền tảng.
- **Koin**: chọn đúng cho multiplatform DI.
- **Expect/Actual pattern**: dùng cho `Startup.kt`, `OSFileUtils`, `SystemThemeDetector` — idiomatic KMP.
- **Base class pattern** cho Decompose components: `Base*Component.kt` chứa shared logic, platform override cho UI-specific.

---

## 5. Cải thiện Tách biệt Module

### 5.1. Đánh giá hiện trạng từng module

| Module | Trách nhiệm kỳ vọng | Trách nhiệm thực tế | Đánh giá |
|---|---|---|---|
| `downloader:core` | Pure download engine | OK — không depend Compose/Koin | ✅ Tốt |
| `downloader:monitor` | The state of downloads | Có UI model + depend Compose | ❌ Sai tên |
| `shared:app` | Shared UI & business logic | God module, 8 api deps | ⚠️ Quá nhiều |
| `shared:utils` | Generic utilities | OK nhưng có JNA — không KMP thật sự | ⚠️ |
| `shared:local-server` | HTTP server | JVM-only, không KMP | ⚠️ Tên misleading |
| `shared:config` | Config abstraction | Dùng bởi `shared:app`, Desktop, Android (9+ file import) | ✅ Đang dùng, cân nhắc KMP hóa |
| `shared:resources` | i18n + assets | Custom Gradle task inline | ✅ Tốt |
| `shared:updater` | Auto-update logic | OK | ✅ Tốt |
| `integration:server` | Browser extension API | OK | ✅ Tốt |

### 5.2. Đề xuất cấu trúc lại

```
HIỆN TẠI:                        ĐỀ XUẤT:
shared:app (god, 8 api deps)      shared:domain          ← pure interfaces + DTOs (no Compose/Koin)
                                  shared:data            ← repositories + DataStore impls
                                  shared:features        ← feature/home, feature/settings, feature/downloads
                                  shared:design-system   ← theme + chamfer widget (reusable)
                                  
downloader:core ← monitor         downloader:core        ← pure engine (không depend monitor)
downloader:monitor (leak UI)      downloader:monitor     ← chỉ state (không Compose)
                                  downloader:monitor-ui  ← UI models (ở shared:features)
```

### 5.3. Module isolation strategy chi tiết

#### A. Quy tắc phân lớp

```
┌─────────────────────────────────────────────────────────┐
│ Presentation Layer (Compose, ViewModel/Component)       │
│   shared:features/* (per-feature modules)               │
│   shared:design-system (reusable UI primitives)         │
└─────────────────┬───────────────────────────────────────┘
                  │ depends on ↓
┌─────────────────▼───────────────────────────────────────┐
│ Domain Layer (pure Kotlin business logic)               │
│   shared:domain (interfaces: DownloadRepository, ...)   │
│   downloader:core (engine)                              │
│   downloader:monitor (state machine)                    │
└─────────────────┬───────────────────────────────────────┘
                  │ depends on ↓
┌─────────────────▼───────────────────────────────────────┐
│ Data Layer (repositories, network, persistence)         │
│   shared:data (impl DownloadRepository, etc.)           │
│   shared:local-server (HTTP server)                     │
│   shared:resources (i18n resources)                     │
└─────────────────────────────────────────────────────────┘
```

**Quy tắc**:
- `domain` không depend gì ngoài stdlib + coroutines.
- `data` depend `domain` + platform libs.
- `features` depend `domain` + `data` + `design-system`.
- `downloader:core` KHÔNG được depend UI / Koin (đã OK).
- `downloader:monitor` tách `UiPart` ra, không depend Compose.

#### B. UseCase / Interactor layer

Hiện tại `DownloadSystem.addDownload()` (`shared/app/.../util/DownloadSystem.kt:70-104`) gọi trực tiếp `downloadManager.addDownload()` — không có abstraction.

**Đề xuất**:
```kotlin
// shared:domain/useCase/AddDownloadsUseCase.kt
interface AddDownloadsUseCase {
    suspend operator fun invoke(input: AddDownloadsInput): Result<List<Long>>
}

// shared:data/useCase/AddDownloadsUseCaseImpl.kt
class AddDownloadsUseCaseImpl(
    private val downloadManager: DownloadManager,
    private val queueManager: QueueManager,
    private val categoryManager: CategoryManager,
    private val completionActions: OnDownloadCompletionActionRunner,
) : AddDownloadsUseCase {
    override suspend fun invoke(input: AddDownloadsInput): Result<List<Long>> = runCatching {
        // ...logic hiện tại ở DownloadSystem.addDownload()
    }
}
```

#### C. Repository abstraction

Hiện tại `DownloadListFileStorage` là concrete class, không có interface.

**Đề xuất**:
```kotlin
// shared:domain/repository/DownloadRepository.kt
interface DownloadRepository {
    fun observeAll(): Flow<List<Download>>
    suspend fun getById(id: Long): Download?
    suspend fun add(item: Download): Result<Unit>
    suspend fun update(item: Download): Result<Unit>
    suspend fun remove(id: Long): Result<Unit>
}

// shared:data/repository/FileSystemDownloadRepository.kt
class FileSystemDownloadRepository(...) : DownloadRepository { ... }

// shared:data/repository/InMemoryDownloadRepository.kt (test)
class InMemoryDownloadRepository : DownloadRepository { ... }
```

#### D. Error handling với `Result`/`Either`

Hiện tại dùng `runCatching {}.onFailure { sendNotification(...) }` rải rác (vd `AppComponent.kt:717-731`).

**Đề xuất tập trung**:
```kotlin
// shared:domain/error/DownloadOpError.kt
sealed class DownloadOpError {
    data object FileNotFound : DownloadOpError()
    data class IOError(val cause: Throwable) : DownloadOpError()
    data object UnsupportedPlatform : DownloadOpError()
    data class ValidationError(val message: String) : DownloadOpError()
}

// shared:domain/usecase/OpenDownloadUseCase.kt
interface OpenDownloadUseCase {
    suspend operator fun invoke(item: IDownloadItem): Either<DownloadOpError, Unit>
}
```

Có thể dùng `kotlin.Result` (stdlib) hoặc `arrow.core.Either` (đã có trong `shared:utils`).

### 5.4. Tách `AppComponent` — decomposition pattern

```
Hiện tại:
AppComponent (1221 dòng) — 12 slot navigations

Đề xuất:
- RootAppComponent — theme, locale, system tray
- HomeRootComponent — quản lý Home + Settings slot
- DialogsRootComponent — quản lý AddDownload/EditDownload/DownloadDialog/CategoryDialog/FileChecksum
- UpdatesRootComponent — quản lý Update + PerHostSettings
- AppActionsComponent — quản lý system integration (notify, beep, exit, etc.)
```

Mỗi child component được inject qua Koin + Decompose `ComponentContext`.

---

## 6. Yêu cầu chuẩn cho ứng dụng KMP phức tạp

### 6.1. Bảng đánh giá hiện trạng

| Pattern | Hiện trạng | Đề xuất áp dụng |
|---|---|---|
| **Clean Architecture / Hexagonal** | ❌ Thiếu UseCase layer | Domain → Data → Presentation, mỗi layer là module riêng |
| **MVI / MVVM** | ✅ Dùng đúng | `ContainsEffects` dùng ở 8+ components (AppComponent, MainComponent, BrowserComponent, FileChecksumComponent, EditDownloadComponent...). `ContainsScreenState` ở FileChecksum. Pattern nhẹ, đúng mức — không over-engineer |
| **UseCase layer** | ❌ Không có | Mỗi use case là 1 class, inject vào Component |
| **Repository pattern** | 🟡 `BaseAppRepository` chỉ wrap settings | Interface `DownloadRepository`, `QueueRepository`, `SettingsRepository` + nhiều impl |
| **Result / Either cho error** | 🟡 `IntegrationResult` (custom sealed), `runCatching` rải rác | Dùng `kotlin.Result` hoặc `arrow.core.Either` xuyên suốt |
| **Module isolation** | 🟡 `shared:app` leak 8 modules | Convention: `api()` chỉ cho public contract, `implementation()` cho nội bộ |
| **Decompose** | ✅ Đúng | Tiếp tục — tốt cho KMP |
| **Koin** | ✅ Đúng | Chia nhỏ Android `Di.kt` theo pattern Desktop |
| **Expect/Actual** | ✅ Đúng | OK |
| **Testing** | 🔴 **0 tests trên 91k LOC** | **Quan trọng nhất**: thêm `commonTest/` cho `DownloadJob`, `TransactionalFileSaver`, `FileNameUtil`; ArchUnit/Konsist cho architecture rules |
| **CI/CD** | 🟡 Chỉ `publish.yml` (5.810 bytes) | Thêm: `lint.yml`, `test.yml` (chạy trên PR), `coverage.yml` |
| **i18n** | 🟡 2 locale (en_US, vi_VN) | Setup Weblate/Crowdin, mở rộng locale |
| **A11y** | 🟡 Yếu | `contentDescription` cho icon-only, `focusOrder`, test với TalkBack/screen reader |
| **Performance monitoring** | ❌ Không có | Baseline Profile cho Android, tracing với `kotlinx-coroutines-debug`, macrobenchmark |
| **Logging structured** | 🟡 Có `Logger.kt` nhưng format chưa chuẩn | Dùng `kotlin-logging` + JSON structured log |
| **Secrets management** | 🟡 Cần audit | Không commit secrets, dùng `BuildConfig` cho API key (nếu có) |
| **Dependency update automation** | 🟡 Có `versions` plugin nhưng không auto-run | Dependabot / Renovate config |
| **Code style enforcement** | 🟡 Không rõ | `ktlint` hoặc `spotless` trong CI |
| **API documentation** | 🟡 Một số file có KDoc, nhiều file không | Convention: public API phải có KDoc |

### 6.2. Testing — thiếu trầm trọng

**Hiện trạng**:
- Không có thư mục `src/test/`, `src/commonTest/`, `src/desktopTest/`, `src/androidTest/`.
- 963 file Kotlin, 91.789 dòng — **0 tests**.
- Không có architecture test (ArchUnit, Konsist).
- Không có UI test (Compose Multiplatform có thể test).

**Đề xuất test infrastructure**:

```
:downloader:core/src/commonTest/       ← pure JVM unit test cho DownloadJob state machine
:downloader:core/src/commonTest/       ← integration test với MockWebServer
:shared:app/src/commonTest/            ← Decompose component test với TestComponentContext
:shared:app/src/commonTest/            ← ViewModel/MVI test
:shared:utils/src/commonTest/          ← utility test
```

**Architecture test với Konsist**:
```kotlin
class ArchitectureTest {
    @Test
    fun `downloader core should not depend on compose`() {
        Konsist
            .scopeFromProject("downloader/core")
            .assertFalse { it.hasImport { import -> import.name.contains("androidx.compose") } }
    }
    
    @Test
    fun `composable parameters should be immutable or stable`() {
        Konsist.scopeFromProject()
            .functions()
            .filter { it.hasAnnotationOf(Composable::class) }
            .assertTrue { function ->
                function.parameters.all { param ->
                    param.type.hasAnnotationOf(Immutable::class) ||
                    param.type.hasAnnotationOf(Stable::class)
                }
            }
    }
}
```

### 6.3. CI/CD — thiếu quality gate

**Hiện trạng**:
- Chỉ 1 workflow: `.github/workflows/publish.yml` (5.810 bytes) → trigger khi release, không phải PR check.

**Đề xuất workflows cần thêm**:
- `lint.yml` — ktlint / spotless + detekt.
- `test.yml` — chạy unit test trên mỗi PR.
- `coverage.yml` — Kover hoặc jacoco, upload lên Codecov.
- `architecture-test.yml` — Konsist / ArchUnit.
- `dependency-update.yml` — Dependabot weekly PR.

---

## 7. Đánh giá phản biện — Đề xuất nào thực sự có giá trị?

### 7.1. Tổng kết sau deep review

| Đề xuất gốc | Verdict | Lý do |
|---|---|---|
| HomePage thiếu LazyColumn | ❌ **SAI** | Cả 2 platform đã dùng LazyColumn/Table. Nhầm Column wrapper với list renderer |
| DownloadMonitor scope leak | ⚠️ **Phóng đại** | Singleton scope = đúng. Chỉ cần Closeable cho test (P2) |
| intervalFlow(500) tốn CPU | ⚠️ **Phóng đại** | Iterate 10-50 items in-memory, không I/O. Chi phí negligible |
| MVI không dùng hết | ❌ **SAI** | ContainsEffects dùng ở 8+ components |
| shared:config orphan | ❌ **SAI** (đã sửa v1.1) | Đang dùng tích cực |
| mac-utils rỗng | ❌ **SAI** (đã sửa v1.1) | Có MacEventHandler.kt |
| **Tách AppComponent 1221 dòng** | ✅ **ĐÚNG** | God component thật — khó maintain, khó test, merge conflict risk |
| **Android Di.kt 640 dòng** | ✅ **ĐÚNG** | Copy-paste risk thật — 90% logic trùng Desktop |
| **UiPart leak Compose vào monitor** | ✅ **ĐÚNG** | Module boundary violation — `@Immutable` annotation kéo Compose Compiler vào data module |
| **api() → implementation()** | ✅ **ĐÚNG** | Build time tăng không cần thiết, transitive deps không cần re-export |
| **HttpDownloadJob 775 dòng** | ✅ **ĐÚNG** | Quá nhiều responsibility — khó unit test từng logic |
| **0 tests** | ✅ **ĐÚNG** | Rủi ro cao nhất — không có safety net khi refactor |
| **runBlocking trong UiModule** | ⚠️ **Nhẹ** | Block main thread lúc init — nhưng chỉ xảy ra 1 lần khi app start, user không cảm nhận |
| **UseCase / Repository layer** | ⚠️ **Cân nhắc** | Đúng lý thuyết, nhưng over-engineering nếu solo dev. ROI thấp trừ khi team scale |
| **DownloadListFileStorage O(n) I/O** | ✅ **ĐÚNG nhưng P2** | Chỉ ảnh hưởng startup time. Trade-off resilience vs speed là chấp nhận được |

### 7.2. Rủi ro và đánh đổi của các đề xuất

#### Tách AppComponent (P0) — Rủi ro: Trung bình
- **Được**: Dễ navigate, dễ test từng phần, giảm merge conflict.
- **Mất**: Phải truyền managers qua child components (nhiều params). Decompose slot phải restructure.
- **Rủi ro**: Nếu tách sai boundary → coupling tăng thay vì giảm. Cần test kỹ navigation flows.
- **Verdict**: Nên làm, nhưng cần plan navigation graph trước.

#### Tái cấu trúc Android Di.kt (P0) — Rủi ro: Thấp
- **Được**: DRY, dễ maintain, sync giữa platform.
- **Mất**: Thời gian refactor + risk regression (DI sai = app crash on start).
- **Rủi ro**: Nếu module shared DI không compile trên cả JVM + Android → build break.
- **Verdict**: Nên làm. Tách file theo Desktop pattern trước, chưa cần move to shared module.

#### api() → implementation() (P1) — Rủi ro: Trung bình-Cao
- **Được**: Giảm compile time (Gradle chỉ recompile affected module), module isolation rõ.
- **Mất**: Phải explicit import ở consumer modules. Breaking change nếu consumer dùng transitive.
- **Rủi ro**: Nếu `desktop:app` hoặc `android:app` đang dùng classes từ `downloader:core` trực tiếp (thông qua transitive từ `shared:app`) → compile error.
- **Verdict**: Nên làm nhưng PHẢI verify toàn bộ import chains trước. Dùng `./gradlew :desktop:app:compileKotlin` sau mỗi thay đổi.

#### UseCase / Repository pattern (P2) — Rủi ro: Over-engineering
- **Được**: Testability, swap implementation, clean separation.
- **Mất**: Boilerplate tăng 30-50%, indirection layers, slower feature development.
- **Rủi ro**: Với solo/small team, thêm UseCase layer cho mỗi operation = thêm file + interface + impl + test cho mỗi tính năng nhỏ. Time-to-feature tăng.
- **Verdict**: Chỉ nên áp dụng CHO CÁC OPERATION PHỨC TẠP (addDownload có logic category + queue + duplicate check). Không cần cho simple CRUD.

#### Test infrastructure (P0) — Rủi ro: Thấp
- **Được**: Safety net cho mọi refactor phía trước. Không refactor nào ở trên nên làm mà KHÔNG CÓ test.
- **Mất**: Thời gian viết test ban đầu.
- **Rủi ro**: Gần như không có. Test tồn tại = good.
- **Verdict**: **ĐÂY LÀ PREREQUISITE cho mọi refactor khác.** Làm TRƯỚC.

### 7.3. Thứ tự thực hiện đề xuất (dependency-aware)

```
1. Test infrastructure     ← PHẢI làm đầu tiên (safety net)
   │
   ├─ 2. Tách AppComponent       ← cần test để verify navigation
   ├─ 3. Tách Android Di.kt      ← cần test để verify DI wiring  
   └─ 4. Tách UiPart             ← cần compile test
         │
         └─ 5. api() → implementation()  ← cần full build verification
               │
               └─ 6. Tách HttpDownloadJob ← cần unit test cho từng piece
```

### 7.4. Những gì KHÔNG nên làm (ngay)

| Đề xuất | Lý do bỏ qua |
|---|---|
| Thêm UseCase layer toàn bộ | Over-engineering cho current scale. Code ĐANG CHẠY tốt, logic rõ ràng |
| Repository interface cho mọi thứ | Chỉ cần nếu muốn swap storage impl (hiện FileSystem là strategy duy nhất hợp lý cho download manager) |
| Thay `intervalFlow(500)` | Không phải bottleneck. Chi phí thay đổi > benefit. Dễ introduce race condition |
| Centralize error handling với Either | Arrow `Either` đã có nhưng `runCatching` stdlib là đủ cho hầu hết cases. Over-abstraction |
| Full Clean Architecture | Download manager không phải enterprise app. Pragmatic > pure |

---

## 8. Bảng ưu tiên hành động (đã điều chỉnh sau deep review)

### 🔴 P0 — Nghiêm trọng, làm ngay

| # | Hành động | Tác động | Effort | File tham chiếu |
|---|---|---|---|---|
| 1 | **Thêm test infrastructure** (Konsist + unit test cho DownloadJob, FileNameUtil, TransactionalFileSaver) | Cao — phát hiện regression, cho phép refactor an toàn | Trung bình | (toàn project) |
| 2 | **Tách `AppComponent` 1221 dòng** thành 4-5 component con | Cao — maintainability, giảm merge conflict | Trung bình | `AppComponent.kt` |
| 3 | **Tái cấu trúc Android `Di.kt`** theo pattern Desktop (10 file module) | Cao — fix bug đồng bộ, dễ maintain | Trung bình | `android/app/.../di/Di.kt` |
| 4 | **Tách `UiPart` từ `downloader:monitor`** | Trung bình — leaky abstraction ảnh hưởng build time | Thấp | `downloader/monitor/.../UiPart.kt` |

### 🟡 P1 — Quan trọng, làm trong 1-2 sprint

| # | Hành động | Tác động | Effort | File tham chiếu |
|---|---|---|---|---|
| 5 | **Đổi `api()` → `implementation()` ở `shared:app`** | Trung bình — module isolation, giảm build time | Thấp | `shared/app/build.gradle.kts:28-36` |
| 6 | **Tách `HttpDownloadJob` 775 dòng** thành các module nhỏ | Trung bình — maintainability, testability | Trung bình | `HttpDownloadJob.kt` |
| 7 | **Thêm CI workflow lint + test + coverage** | Trung bình — quality gate | Thấp | `.github/workflows/` |
| 8 | **Thêm a11y semantics** + test | Trung bình — UX cho người khuyết tật | Trung bình | (UI components) |
| 9 | **Cache `DownloadListFileStorage.getAll()`** trong memory | Thấp-Trung bình — startup time khi 1000+ items | Thấp | `DownloadListFileStorage.kt` |

### 🟢 P2 — Cải thiện dài hạn

| # | Hành động | Tác động | Effort |
|---|---|---|---|
| 10 | **Implement `Closeable` cho `DownloadMonitor`** — cho testability | Thấp — chỉ thêm interface | Thấp |
| 11 | **KMP hóa `shared:config`** hoặc gộp vào `shared:utils` | Thấp — consistency | Thấp |
| 12 | **`intervalFlow` adaptive** — 1000ms khi background, 500ms foreground | Thấp — tiết kiệm pin mobile | Thấp |
| 13 | **Thêm UseCase layer** cho `addDownload`, `pauseDownload`, `resumeDownload` | Trung bình — testability | Trung bình |
| 14 | **Thêm Repository abstraction** cho download list, queue, settings | Trung bình — testability | Trung bình |
| 15 | **Centralize error handling** với `Result`/`Either` | Trung bình — debugability | Trung bình |
| 16 | **Sửa README** — đang nói "5 themes" nhưng thực tế 6 | Thấp — branding | Thấp |
| 17 | **Mở rộng i18n** — thêm zh_CN, ja, ko, de, fr, es | Thấp — reach | Trung bình |
| 18 | **Setup Crowdin/POEditor** cho community translation | Thấp — reach | Trung bình |
| 19 | **Convention plugin refactor** — extract shared config | Thấp — DX | Thấp |
| 20 | **Gộp `desktop:mac-utils` vào `desktop:app-utils`** nếu muốn giảm module count | Thấp — cleanup | Thấp |
| 21 | **Baseline Profile cho Android** | Trung bình — startup time | Trung bình |
| 22 | **Macrobenchmark** cho DownloadManager | Trung bình — perf monitoring | Trung bình |

---

## Phụ lục: Công cụ hỗ trợ

### GitNexus MCP

Project đã được GitNexus index sẵn (13.859 symbols, 47.710 relationships, 300 execution flows). Theo AGENTS.md, trước khi sửa bất kỳ symbol nào:

1. `gitnexus_context({name: "..."})` để xem callers/callees.
2. `gitnexus_impact({target: "...", direction: "upstream"})` để đánh giá blast radius.
3. `gitnexus_detect_changes()` trước khi commit.

Vì khối lượng code lớn (~92k LOC), mỗi refactor nên chạy GitNexus trước để tránh regression.

### Tham chiếu file:line tổng hợp

| File | Dòng | Vấn đề |
|---|---|---|
| `downloader/monitor/.../DownloadMonitor.kt` | 28 | Scope singleton — OK, thêm `Closeable` cho test |
| `downloader/monitor/.../DownloadMonitor.kt` | 254-258 | intervalFlow(500) — hợp lý, tối ưu nhẹ nếu cần |
| `downloader/monitor/.../UiPart.kt` | 3 | Leak Compose dependency vào monitor module |
| `downloader/core/.../HttpDownloadJob.kt` | — | 775 dòng, cần tách |
| `downloader/core/.../db/DownloadListFileStorage.kt` | 22-34 | O(n) I/O khi boot — cache cải thiện startup |
| `desktop/app/.../pages/home/sections/DownloadList.kt` | — | ✅ Đã dùng Table (LazyColumn) — không có vấn đề |
| `android/app/.../pages/home/DownloadList.kt` | 59 | ✅ Đã dùng LazyColumn — không có vấn đề |
| `shared/app/build.gradle.kts` | 28-36 | 8 api deps — nên giảm xuống implementation |
| `shared/app/.../util/DownloadSystem.kt` | 70 | Không có UseCase abstraction (P2) |
| `shared/app/.../util/mvi/` | — | ✅ ContainsEffects dùng ở 8+ components |
| `shared/app/.../util/BaseComponent.kt` | 11-24 | ✅ Pattern tốt |
| `shared/config/` | — | Đang dùng tích cực, JVM-only |
| `desktop/app/.../AppComponent.kt` | — | 1221 dòng, cần tách |
| `desktop/app/.../di/UiModule.kt` | 70-77 | runBlocking — cân nhắc thay thế |
| `android/app/.../di/Di.kt` | — | 640 dòng monolithic, cần tách |

---

**Người thực hiện khảo sát**: Claude Code (opencode)
**Ngày hoàn thành**: 2026-07-05
**Version khảo sát**: 1.2 (đã double-check với source code thực tế)

### Changelog
- **v1.2** (2026-07-05): Deep review — xác minh từng đề xuất với source code thực tế:
  1. **BÁC BỎ mục 2.2**: Cả Desktop (Table→LazyColumn) và Android (LazyColumn+LazyVerticalGrid) đều đã virtualized. Không có perf issue ở rendering list.
  2. **HẠ MỨC mục 2.1**: `DownloadMonitor.scope` là singleton by-design (Koin `single`). Không phải memory leak — scope sống cùng app process. Chỉ cần `Closeable` cho testability (P2).
  3. **HẠ MỨC mục 2.3**: `intervalFlow(500)` operate trên list nhỏ (chỉ non-completed downloads, thường <50 items), đọc in-memory StateFlow — rất nhẹ. Không phải perf bottleneck.
  4. **SỬA mục MVI**: `ContainsEffects` đang được dùng tích cực ở 8+ components — không phải "không dùng hết".
  5. Giữ nguyên các đề xuất structural (tách AppComponent, tái cấu trúc Android Di.kt, tách UiPart, api→implementation) — đây là cải thiện thực sự.
- **v1.1** (2026-07-05): Sửa 4 lỗi chính xác:
  1. `desktop:mac-utils` không rỗng — chứa `MacEventHandler.kt` (42 dòng, xử lý macOS events).
  2. `shared:config` không orphan — `shared:app` depend `api(project(":shared:config"))`, 9+ file import nó.
  3. `HomePage.kt` nằm ở `desktop/app/` và `android/app/`, không phải `shared/app/`.
  4. Convention plugins: mô tả chính xác hơn (body giống nhau, pattern phổ biến nhưng vi phạm DRY).
