# Báo cáo Khảo sát & Đề xuất Cải thiện — Flow Download Manager

> **Repo**: `flowspeed.link` · **Ngày khảo sát**: 2026-07-05
> **Cập nhật lần cuối**: 2026-07-19 (chuẩn hóa bộ Claude Code project skills theo boundary và rủi ro)
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
8. [✅ Đã thực hiện — Kết quả thực tế](#8--đã-thực-hiện--kết-quả-thực-tế)
9. [Bảng ưu tiên hành động — Còn lại](#9-bảng-ưu-tiên-hành-động--còn-lại)
10. [Tái đánh giá 2026-07-12 — quyết định theo rủi ro](#10-tái-đánh-giá-2026-07-12--quyết-định-theo-rủi-ro)
11. [Trạng thái phần mềm hiện tại — cập nhật 2026-07-19](#11-trạng-thái-phần-mềm-hiện-tại--cập-nhật-2026-07-19)
12. [Bộ Claude Code project skills — cập nhật 2026-07-19](#12-bộ-claude-code-project-skills--cập-nhật-2026-07-19)

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

#### 2.6. `AppComponent` — ⚡ Giảm từ 1221 → 1046 dòng (commit `5e39801`)
**File**: `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/AppComponent.kt`

**Đã thực hiện**: Wire `NotificationDelegate` và `DownloadOperationsDelegate` (đã tồn tại nhưng chưa được dùng). Xóa ~175 dòng inline duplicate:
- `sendNotification`/`sendDialogNotification`/`beep`/`showNotification` → `notificationDelegate`
- `onNewDownloadEvent` (60 dòng) → `notificationDelegate.onNewDownloadEvent`
- `openDownloadItem`/`openDownloadItemFolder` → `downloadOpsDelegate`
- `addDownloads`/`addDownload`/`startNewDownload` → `downloadOpsDelegate`
- Xóa duplicate `dialogMessages`/`onDismissDialogMessage`/`newDialogMessage`

**Còn lại (P2)**: Tách navigation slots thành sub-components — cần plan navigation graph trước khi thực hiện.

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

#### 3.4. ✅ 6 themes — ĐÃ SỬA README (commit `db690c8`)
**File**: `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/theme/DefaultThemes.kt:155-164`

1. `dark` — "Cyber Dark"
2. `light` — "Cyber Light"
3. `obsidian` — "Terminal"
4. `deepOcean` — "Deep Ocean"
5. `black` — "OLED Black"
6. `lightGray` — "Industrial"

README đã được cập nhật từ "5 themes" → "6 themes". Design system mạnh, custom (không Material3).

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

#### 4.1. `shared:app` là "god module" — ⚡ Đã giảm một phần (commit `4b2a12f`, nhưng một số revert)
**File**: `shared/app/build.gradle.kts`

**Đã thực hiện**: Đổi `shared:auto-start` và `markdownRenderer.core` từ `api()` → `implementation()`.

**Phải revert**:
- `markdownRenderer.core` → revert về `api()`: `android:app/pages/updater/NewUpdatePage.kt` import trực tiếp `com.mikepenz.markdown.compose.Markdown`
- `shared:auto-start` → revert về `api()`: `android:app/di/PlatformModule.kt` dùng `Startup`/`AbstractStartupManager` trực tiếp

**Trạng thái hiện tại** (sau revert): Chỉ `markdownRenderer.core` và `shared:auto-start` vẫn là `api()` vì consumer modules import trực tiếp.

**Lesson learned**: Trước khi đổi `api()` → `implementation()`, phải grep TOÀN BỘ consumer modules (không chỉ `android:app` và `desktop:app`). Wildcard imports trong file cũ ẩn nhiều dependencies.

**Còn cơ hội** (P1): Các module sau consumer không import trực tiếp — có thể đổi an toàn sau khi verify kỹ:
- `shared:resources` (chỉ dùng qua `stringResource()` từ `shared:app` internals)

```kotlin
// Hiện tại — sau revert
api(project(":downloader:core"))        // consumers import trực tiếp ✓ giữ api
api(project(":downloader:monitor"))     // consumers import trực tiếp ✓ giữ api
api(project(":shared:config"))          // consumers import trực tiếp ✓ giữ api
api(project(":shared:utils"))           // consumers import trực tiếp ✓ giữ api
api(project(":shared:compose-utils"))   // consumers import trực tiếp ✓ giữ api
api(project(":shared:resources"))       // cần verify thêm
api(project(":shared:auto-start"))      // PlatformModule.kt dùng trực tiếp ✓ giữ api
api(project(":shared:updater"))         // consumers import UpdateManager ✓ giữ api
api(libs.markdownRenderer.core)         // NewUpdatePage.kt import trực tiếp ✓ giữ api
```

#### 4.2. ✅ Android `Di.kt` 640 dòng — ĐÃ TÁCH (commit `dde4da6`)

**Đã thực hiện**: Tách `Di.kt` 640 dòng thành 9 file module nhỏ theo pattern Desktop:

| File mới | Nội dung |
|---|---|
| `DownloaderModule.kt` | Download engine (DB, HTTP client, manager, queue, monitor) |
| `DownloadSystemModule.kt` | DownloadSystem facade, categories, event runners |
| `NetworkModule.kt` | OkHttpClient, SSL factory, hostname verifier |
| `SerializationModule.kt` | kotlinx.serialization Json + polymorphic config |
| `StorageModule.kt` | DataStore settings, proxy, per-host, page states |
| `UpdaterModule.kt` | GitHub update checker, update applier, UpdateManager |
| `PlatformModule.kt` | Android context, startup, version tracking, app managers |
| `UiModule.kt` | Theme, language, icons, notifications |
| `Di.kt` | Composition root only (includes all modules) |

> **Lesson learned**: Khi tách, chú ý import packages chính xác — wildcard imports trong file cũ (`import com.flowspeed.link.shared.util.*`) ẩn nhiều class thực ra thuộc `shared:app` không phải `downloader:core`. `DownloadFoldersRegistry` ở `com.flowspeed.link.shared.util`, không phải `com.flowspeed.lib.downloader.db`.

#### 4.3. ✅ `downloader:monitor` composeBase plugin — ĐÃ XÓA (commit `bce62ca`)

**Đã thực hiện**: Bỏ `id(MyPlugins.composeBase)` khỏi `downloader/monitor/build.gradle.kts`. Module không có `@Composable` functions nên không cần Kotlin Compose Compiler plugin. `compose.runtime` vẫn giữ để dùng `@Immutable` annotation.

**Còn lại (P2)**: Tách `UiPart.kt` ra module riêng nếu muốn `downloader:monitor` hoàn toàn không depend Compose runtime.

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
| **Testing** | 🟡 Có 11 file test; core desktop suite hiện có 89 test cases | Tiếp tục theo luồng rủi ro cao: retry exhaustion, job lifecycle resume/cancel, queue, monitor và navigation; chưa cần coverage gate |
| **CI/CD** | ✅ Có `build-check.yml` và `publish.yml`; CI chạy toàn bộ unit-test module hiện có | Cân nhắc coverage report sau khi suite đủ đại diện; chưa cần workflow test riêng |
| **i18n** | 🟡 2 locale (en_US, vi_VN) | Setup Weblate/Crowdin, mở rộng locale |
| **A11y** | 🟡 Yếu | `contentDescription` cho icon-only, `focusOrder`, test với TalkBack/screen reader |
| **Performance monitoring** | ❌ Không có | Baseline Profile cho Android, tracing với `kotlinx-coroutines-debug`, macrobenchmark |
| **Logging structured** | 🟡 Có `Logger.kt` nhưng format chưa chuẩn | Dùng `kotlin-logging` + JSON structured log |
| **Secrets management** | 🟡 Cần audit | Không commit secrets, dùng `BuildConfig` cho API key (nếu có) |
| **Dependency update automation** | 🟡 Có `versions` plugin nhưng không auto-run | Dependabot / Renovate config |
| **Code style enforcement** | 🟡 Không rõ | `ktlint` hoặc `spotless` trong CI |
| **API documentation** | 🟡 Một số file có KDoc, nhiều file không | Convention: public API phải có KDoc |

### 6.2. Testing — đã có nền tảng, coverage còn hẹp

**Hiện trạng tái kiểm chứng ngày 2026-07-19**:
- Có 11 file `*Test.kt`: 10 file trong `downloader/core/src/commonTest/`, 1 file trong `shared/utils/src/commonTest/`.
- `:downloader:core:desktopTest` chạy 89 test cases, 0 failure, 0 error.
- `:shared:utils:desktopTest` chạy thành công.
- PowerShell runner chạy thành công cả hai module qua Gradle wrapper.
- Test mới không dùng internet thật, user filesystem, wall-clock sleep hoặc global state phụ thuộc thứ tự.
- Chưa có architecture test, UI test, DI smoke test hoặc navigation smoke test.
- Job-level ETag/length/resume lifecycle, retry exhaustion, pause/cancel worker transition, queue và monitor vẫn chưa được khóa bằng test deterministic.

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

### 6.3. CI/CD — ✅ Đã thêm build-check workflow và core unit test (commit `3297b60` + cập nhật sau đó)

**Đã thực hiện**:
- `.github/workflows/build-check.yml` — trigger trên mỗi PR và push vào `main`/`dev`:
  - **Compile job**: `compileKotlinDesktop` + `compileDebugKotlin` — phát hiện compile errors sớm
  - **Unit test**: `:downloader:core:desktopTest` + `:shared:utils:desktopTest` + upload report
  - **Android Lint job**: `:android:app:lintDebug` — upload HTML report
- `.github/dependabot.yml` — weekly Gradle + GitHub Actions updates (grouped: compose, kotlin, koin)

**Cũng đã fix các lint errors pre-existing** (commits `da0ece2`, `9d0b0e1`):
- `MissingPermission` trong `AndroidGlobalExceptionHandler.kt` và `FlowServiceNotificationManager.kt` — thêm `@Suppress` với comment giải thích
- `IntentFilterExportedReceiver` trong generated manifest (`AndroidManifest.xml.hbs`) — thêm `android:exported="true"` vào Handlebars template

**Còn lại (P2)**:
- Thêm module test mới vào cùng bước unit test khi module đó có suite đáng chạy; chưa cần tách thành `test.yml` riêng.
- `coverage.yml` — Kover/jacoco + Codecov
- `ktlint`/`detekt` — cần team agree on ruleset trước

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
| **Thiếu tests** | ⚠️ **ĐÚNG MỘT PHẦN** | Đã có 11 file test, core suite 89 cases và CI core+utils; safety net vẫn thiếu cho lifecycle retry/resume/cancel/queue/navigation |
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

#### Test infrastructure (đánh giá lịch sử; hiện đã có nền tảng) — Rủi ro: Thấp
- **Được**: Safety net cho mọi refactor phía trước. Không refactor nào ở trên nên làm mà KHÔNG CÓ test.
- **Mất**: Thời gian viết test ban đầu.
- **Rủi ro**: Gần như không có. Test tồn tại = good.
- **Verdict**: **ĐÂY LÀ PREREQUISITE cho mọi refactor khác.** Làm TRƯỚC.

### 7.3. Thứ tự đề xuất lịch sử (đã được thay thế bởi mục 10)

```
1. Test infrastructure     ← PHẢI làm đầu tiên (safety net; hiện đã có nền tảng ban đầu)
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

## 8. ✅ Đã thực hiện — Kết quả thực tế

> **Branch**: `refactor/structural-improvements` → merged vào `dev`
> **CI status**: Compile ✅ Android Lint ✅ (tất cả 4/4 checks xanh)

| # | Commit | Thay đổi | Kết quả |
|---|---|---|---|
| 1 | `bce62ca` | Bỏ `composeBase` plugin khỏi `downloader:monitor` | Loại bỏ Kotlin Compose Compiler không cần thiết |
| 2 | `dde4da6` | Tách Android `Di.kt` 640 dòng → 9 file modules | Mirrors Desktop pattern, dễ maintain |
| 3 | `4b2a12f` + fixes | Thử đổi `api()` → `implementation()` | Chỉ thành công với một phần nhỏ; phần lớn phải revert vì consumer imports |
| 4 | `85d764c` | Revert split `HttpDownloadJob` extension files | Extension functions không access được `protected` members của parent class |
| 5 | `3297b60` | CI `build-check.yml` + Dependabot | PR protection + weekly dep updates |
| 6 | `5e39801` | Wire delegates vào `AppComponent` (-175 dòng) | 1221 → 1046 dòng, logic tập trung |
| 7 | `db690c8` | README: 5 → 6 themes | Chính xác |
| 8 | `e9959b5` | IMPROVEMENTS.md v1.2 | Tài liệu audit |
| 9 | `da0ece2` | Suppress 4 `MissingPermission` lint errors | Pre-existing, đã handle đúng cách |
| 10 | `9d0b0e1` | `android:exported="true"` trong Handlebars template | Fix Android 12+ lint requirement |

### Lesson learned từ batch refactor này

1. **Extension functions không thể access `protected` members** — không phải giải pháp để tách large class khi class dùng inheritance. Phải dùng delegation hoặc composition thay thế.

2. **Wildcard imports ẩn package locations** — `import com.flowspeed.lib.downloader.db.*` trong file cũ che giấu rằng `DownloadFoldersRegistry` thực ra ở `com.flowspeed.link.shared.util`. Khi tách file phải grep package từng class, không đoán.

3. **Trước khi đổi `api()` → `implementation()`** phải grep ALL consumers, không chỉ `android:app`/`desktop:app` — bao gồm tất cả file trong các module đó, đặc biệt là tìm cả indirect consumers qua wildcard.

4. **CI là safety net quan trọng** — 10 commits fix bugs trước khi push. Không có CI từ trước nên các bugs này ẩn trong code base.

---

## 9. Bảng ưu tiên hành động — Còn lại

### � P0 — Quan trọng nhất còn chưa làm

| # | Hành động | Tác động | Effort | Ghi chú |
|---|---|---|---|---|
| 1 | **Mở rộng behavioral tests** cho download core | Cao — safety net cho engine | Trung bình | Ưu tiên retry/resume/cancel/range validation/persistence recovery |
| 2 | **Tách `HttpDownloadJob` theo composition, từng phần nhỏ** | Trung bình-Cao — giảm coupling trong engine | Trung bình | Chỉ làm sau khi logic tương ứng có test |

### 🟡 P1 — Nên làm

| # | Hành động | Tác động | Effort | Ghi chú |
|---|---|---|---|---|
| 3 | **Audit a11y theo control tương tác** | Trung bình — UX | Trung bình | Đã có 110 hit semantics/contentDescription; phân biệt icon trang trí với nút thiếu nhãn |
| 4 | **Thêm smoke test DI/navigation** | Trung bình-Cao — bảo vệ app startup | Trung bình | Là điều kiện trước khi tách `AppComponent` |
| 5 | **Tách `UiPart.kt` khỏi monitor nếu đo được build/dependency cost** | Thấp-Trung bình | Thấp | Boundary sạch hơn nhưng lợi ích runtime gần như không có |

### 🟢 P2 — Cải thiện dài hạn

| # | Hành động | Tác động | Effort |
|---|---|---|---|
| 6 | **Implement `Closeable` cho `DownloadMonitor`** | Thấp — testability | Thấp |
| 7 | **Đo startup với lịch sử lớn trước khi cache storage** | Trung bình — quyết định bằng dữ liệu | Thấp |
| 8 | **Mở rộng i18n theo nhu cầu người dùng** | Thấp — reach | Trung bình |
| 9 | **Sửa Gradle deprecation trước Gradle 10** | Trung bình — build future-proof | Trung bình |

---

## 10. Tái đánh giá 2026-07-12 — quyết định theo rủi ro

### 10.1. Phạm vi và bằng chứng mới

Khảo sát này tái kiểm chứng trực tiếp source hiện tại, không chỉ kế thừa kết luận v1.3:

- Working tree sạch tại thời điểm khảo sát.
- Có 11 file test; core desktop suite hiện chạy 89 test cases.
- `:downloader:core:desktopTest` và `:shared:utils:desktopTest` đều `BUILD SUCCESSFUL`.
- CI config compile Desktop + Android, chạy core + utils unit test và Android lint.
- `AppComponent.kt` hiện 1.069 dòng; `HttpDownloadJob.kt` vẫn 775 dòng.
- `shared:app` vẫn re-export nhiều dependency qua `api()`.
- Có 110 vị trí `contentDescription`/`semantics`; nhiều `contentDescription = null` có thể là icon trang trí, không thể kết luận tất cả là lỗi a11y bằng grep.
- Build báo Gradle deprecated features, sẽ không tương thích Gradle 10. `shared:utils` test compile còn warning về opt-in Compose không resolve.

### 10.2. Tiêu chí quyết định

Mỗi đề xuất được cân theo 4 yếu tố:

| Yếu tố | Câu hỏi |
|---|---|
| Lợi ích | Có giảm bug, giảm thời gian maintain hoặc cải thiện UX đo được không? |
| Rủi ro thay đổi | Có chạm luồng download, persistence, DI hoặc navigation quan trọng không? |
| Safety net | Test hiện tại có phát hiện regression của thay đổi này không? |
| Chi phí cơ hội | Thời gian bỏ ra có tốt hơn việc sửa bug hoặc thêm feature người dùng cần không? |

Quy ước quyết định:

- **LÀM**: lợi ích rõ, rủi ro kiểm soát được, có cách verify.
- **LÀM CÓ ĐIỀU KIỆN**: chỉ làm sau test hoặc measurement cụ thể.
- **HOÃN**: vấn đề thật nhưng ROI hiện thấp hoặc thiếu dữ liệu.
- **BỎ QUA**: thay đổi mang tính sạch lý thuyết, không giải quyết vấn đề thực tế hiện tại.

### 10.3. Ma trận lợi ích, rủi ro và quyết định

| Đề xuất | Lợi ích | Rủi ro nếu làm | Rủi ro nếu không làm | Quyết định | Điều kiện / cách làm |
|---|---|---|---|---|---|
| Mở rộng test download core | Rất cao | Thấp-Trung bình: fake sai có thể tạo test vô nghĩa | Cao: regression retry/resume/cancel khó phát hiện | **LÀM NGAY** | Test behavior và failure path; ưu tiên MockWebServer/fake deterministic, không chase coverage % |
| Tách `HttpDownloadJob` | Cao | Cao: race, lock, retry và resume dễ đổi semantics | Trung bình-Cao: file tiếp tục khó sửa | **LÀM CÓ ĐIỀU KIỆN** | Viết characterization test cho phần định tách; extract pure policy/validator bằng composition; mỗi PR một responsibility |
| Tách toàn bộ `AppComponent` | Trung bình | Cao: navigation slot/lifecycle/DI regression | Trung bình: maintain khó, merge conflict | **HOÃN** | Trước hết thêm smoke test DI + navigation; chỉ extract boundary có ownership rõ, không redesign toàn graph |
| Thêm UseCase/Clean Architecture toàn repo | Thấp-Trung bình | Cao: boilerplate, migration dài, nhiều indirection | Thấp | **BỎ QUA** | Chỉ extract operation phức tạp khi cần test độc lập; không tạo interface cho CRUD đơn giản |
| Đổi hàng loạt `api()` sang `implementation()` | Trung bình | Trung bình-Cao: consumer break vì transitive imports | Thấp-Trung bình: build coupling tiếp tục | **HOÃN** | Đo build time trước; đổi từng dependency, compile mọi consumer; không batch refactor |
| Tách `UiPart` khỏi monitor | Thấp-Trung bình | Thấp: package/module move có thể lan import | Thấp: chỉ còn dependency annotation | **HOÃN** | Chỉ làm khi cleanup này mở khóa module isolation hoặc giảm build time đo được |
| Cache `DownloadListFileStorage.getAll()` | Có thể trung bình với lịch sử lớn | Trung bình: stale cache, tăng complexity đồng bộ | Thấp nếu dữ liệu phổ biến nhỏ | **LÀM CÓ ĐIỀU KIỆN** | Benchmark startup với 100/1.000/10.000 item trước; ưu tiên index metadata hơn cache mutable nếu bottleneck thật |
| Adaptive `intervalFlow(500)` | Thấp | Trung bình: stale UI/race lifecycle | Thấp: polling hiện nhẹ | **BỎ QUA HIỆN TẠI** | Chỉ xem lại khi profiler cho thấy pin/CPU đáng kể |
| `Closeable` cho `DownloadMonitor` | Thấp-Trung bình cho test | Thấp | Thấp hiện tại, tăng khi test monitor | **LÀM KHI VIẾT TEST MONITOR** | Thêm lifecycle contract cùng test idempotent close; không đổi singleton behavior trước đó |
| Audit a11y | Trung bình-Cao | Thấp | Trung bình-Cao với người dùng screen reader/keyboard | **LÀM DẦN** | Audit control tương tác theo màn hình; không thay mọi `null`, vì icon trang trí nên giữ `null` |
| Mở rộng i18n hàng loạt | Thấp nếu chưa có demand | Trung bình: chi phí duy trì string và QA | Thấp | **HOÃN** | Dựa analytics/issue/community; setup translation platform trước khi thêm nhiều locale |
| KMP hóa `shared:config` | Thấp | Trung bình: migration và source-set complexity | Thấp | **BỎ QUA** | Chỉ làm nếu target mới không chạy JVM hoặc Android cần dùng module độc lập |
| Gộp `desktop:mac-utils` | Rất thấp | Thấp-Trung bình: mất isolation platform | Rất thấp | **BỎ QUA** | Module nhỏ nhưng boundary platform rõ; số dòng ít không phải lý do đủ để gộp |
| Centralize mọi error bằng `Either` | Thấp-Trung bình | Trung bình: churn API, mixed error model | Thấp | **BỎ QUA TOÀN CỤC** | Dùng typed error cho boundary cần recovery; giữ `runCatching` cho thao tác cục bộ |
| Konsist/architecture tests | Trung bình | Thấp-Trung bình: rule cứng gây false positive | Trung bình khi tiếp tục modularize | **LÀM NHỎ** | Bắt đầu 2-3 invariant có giá trị: core không phụ thuộc UI, platform package không leak common, dependency direction |
| Coverage gate/Codecov ngay | Thấp lúc đầu | Trung bình: khuyến khích test nông, CI noise | Thấp | **HOÃN** | Thu thập coverage không gate trước; đặt gate sau khi baseline ổn định |
| Sửa Gradle deprecated features | Trung bình | Thấp-Trung bình | Trung bình-Cao khi lên Gradle 10 | **LÀM CÓ KẾ HOẠCH** | Chạy `--warning-mode all`, phân loại warning project/plugin, sửa trước upgrade wrapper |
| Thêm `shared:utils:desktopTest` vào CI | Trung bình | Thấp: tăng ít thời gian CI | Trung bình: test hiện có không được bảo vệ trên PR | **LÀM NGAY** | Thêm task vào bước unit test hiện tại, không cần workflow mới |

### 10.4. Phương án thực thi khuyến nghị

#### Giai đoạn 1 — safety net có mục tiêu

1. Thêm `:shared:utils:desktopTest` vào CI.
2. Bổ sung test cho retry limit, response validation, range/resume mismatch, cancel và persistence recovery.
3. Thêm 2-3 architecture invariant, không đưa rule style chủ quan vào test.
4. Thêm smoke test khởi tạo Koin module và navigation chính nếu test harness Decompose ổn định.

**Exit criteria**:

- Test fail được khi cố ý phá retry/range/resume behavior.
- CI chạy toàn bộ test module hiện có.
- Test không phụ thuộc network thật hoặc timing không deterministic.

#### Giai đoạn 2 — giảm rủi ro trong download engine

1. Chọn một responsibility pure trong `HttpDownloadJob`, ưu tiên response validation hoặc retry policy.
2. Viết characterization test cho behavior hiện tại.
3. Extract bằng composition, giữ API và state transition cũ.
4. Mỗi PR chỉ extract một phần; chạy compile Desktop/Android và core tests.

**Không làm trong cùng batch**:

- Không đồng thời đổi locking, coroutine scope, retry semantics hoặc persistence format.
- Không tách file chỉ để giảm line count nếu coupling không giảm.

#### Giai đoạn 3 — cải thiện UX và maintainability theo dữ liệu

1. Audit a11y từng màn hình, ưu tiên Home, Add Download, Settings, Browser.
2. Benchmark startup với lịch sử download lớn trước khi thêm cache/index.
3. Đo build time trước khi giảm `api()` dependencies.
4. Chỉ tách `AppComponent` sau khi có DI/navigation smoke tests và boundary rõ.

### 10.5. Những việc nên bỏ qua hiện tại

- Full Clean Architecture hoặc UseCase class cho mọi thao tác.
- Repository interface cho storage chỉ có một implementation thực tế.
- Adaptive polling khi chưa có profiler evidence.
- KMP hóa `shared:config` chỉ để đồng nhất tên module.
- Gộp `desktop:mac-utils` chỉ vì module nhỏ.
- Chuyển toàn bộ error handling sang `Either`.
- Thay mọi `contentDescription = null`; icon trang trí cần bị loại khỏi accessibility tree.
- Coverage gate cứng trước khi test suite đủ đại diện.

### 10.6. Kết luận cập nhật

Codebase không ở trạng thái cần rewrite. Kiến trúc KMP, Decompose, Koin và module engine hiện đủ tốt để phát triển tiếp. Rủi ro lớn nhất không phải thiếu layer kiến trúc, mà là behavior phức tạp của download engine và navigation chưa có safety net tương xứng.

Phương thức nên chọn:

1. **Test theo rủi ro, không theo tỷ lệ file hoặc coverage.**
2. **Refactor nhỏ bằng composition, luôn khóa behavior bằng characterization test trước.**
3. **Đo trước khi tối ưu storage, polling hoặc build graph.**
4. **Bỏ qua refactor thuần lý thuyết không có user impact hoặc maintenance payoff rõ.**

Thứ tự này cho ROI tốt nhất: giảm regression trước, giảm coupling lõi sau, tối ưu và tái cấu trúc lớn chỉ khi có bằng chứng.

---

## 11. Trạng thái phần mềm hiện tại — cập nhật 2026-07-19

### 11.1. Mức độ sản phẩm

Flow Download Manager hiện là sản phẩm đa nền tảng có engine, UI, persistence, integration và packaging thực tế; không còn ở mức skeleton hoặc proof of concept. Repo có entry point Desktop/Android, landing page, browser extension, CI build check và release workflow.

Khả năng đã có trong source:

- HTTP/HTTPS download, ranged parts và multi-connection download.
- HLS/m3u8 download.
- Pause/resume, retry, persisted download/part state và destination handling.
- Queue, bandwidth limit, scheduling và completion actions.
- Compose UI dùng chung cho Desktop/Android, Decompose navigation và Koin DI.
- Desktop system tray, background behavior, native notification và auto-start.
- Browser extension giao tiếp với app qua localhost port `15151`.
- Sáu theme, tiếng Anh + tiếng Việt.
- Packaging configuration cho Windows, macOS, Linux và Android.

### 11.2. Mức xác minh hiện tại

| Phạm vi | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| Download core unit tests | ✅ Pass | 89 desktop test cases, 0 failure/error |
| Shared utils unit tests | ✅ Pass | Gradle task thành công |
| PowerShell fast runner | ✅ Pass | Chạy core + utils qua wrapper |
| Linux/macOS shell runner | 🟡 Chưa chạy trên host hiện tại | Script dùng POSIX `sh`, cùng task set với PowerShell runner |
| CI unit-test config | ✅ Đã cập nhật | Core + utils trong cùng unit-test step |
| Desktop/Android compile của commit trước | ✅ CI đã xanh theo lịch sử | Batch hiện tại không đổi production code |
| Full runner của working tree hiện tại | 🟡 Chưa chạy | Chưa tái xác minh compile Desktop + Android + Android lint bằng `-Full` |
| UI/DI/navigation automated tests | ❌ Chưa có | Rủi ro startup/navigation chưa được khóa bằng smoke test |
| Release artifact trên mọi OS | 🟡 Không xác minh trong batch | Cần matrix/release workflow hoặc host tương ứng |

### 11.3. Đánh giá sẵn sàng

Phần mềm đủ nền tảng để tiếp tục phát triển và chạy thử thực tế. Download core đã có safety net tốt hơn cho parser, part validation, persistence state và utility behavior. Kiến trúc không cần rewrite.

Chưa thể xem hardening hoàn tất vì:

- `HttpDownloadJob` vẫn gom nhiều responsibility và có coroutine/thread lifecycle phức tạp.
- Job-level ETag/content-length/resume validation chưa có test qua public lifecycle.
- Retry exhaustion và pause/cancel transition chưa có test deterministic.
- Queue/monitor, DI startup, navigation và UI interaction chưa có automated coverage.
- Accessibility còn thiếu audit screen reader/keyboard có hệ thống.
- Gradle deprecated features cần xử lý trước Gradle 10.

Ưu tiên đúng: hoàn thiện lifecycle safety net, thêm DI/navigation smoke test, rồi mới tách `HttpDownloadJob` hoặc `AppComponent`. Không thêm Clean Architecture toàn repo và không refactor chỉ để giảm line count.

### 11.4. Working tree tại thời điểm cập nhật

Thay đổi test/tooling/CI của batch chưa commit. `IMPROVEMENTS.md` và `.claude/skills/` đã có thay đổi tài liệu/hướng dẫn trong working tree. Không có production source được sửa bởi batch HTTP safety net.

---

## 12. Bộ Claude Code project skills — cập nhật 2026-07-19

### 12.1. Mục tiêu

Chuẩn hóa workflow theo boundary thực tế của repo, giảm hướng dẫn trùng lặp và tránh lưu thông tin dễ lỗi thời trong skill. Mỗi skill chỉ giữ trigger, invariant, workflow và cách verify; module graph, CI matrix, artifact matrix và trạng thái triển khai phải đọc từ source of truth hiện tại.

GitNexus vẫn là lớp bảo vệ chung qua `CLAUDE.md`: chạy upstream impact trước khi sửa symbol, cảnh báo khi rủi ro HIGH/CRITICAL và chạy change detection trước commit. Project skills bổ sung invariant riêng cho từng domain, không thay thế GitNexus.

### 12.2. Bộ skill chuẩn

| Skill | Phạm vi chính | Boundary |
|---|---|---|
| `flowspeed-a11y-ux` | UX, keyboard, screen reader, responsive/error states | Review trải nghiệm; thay đổi Compose thuộc `flowspeed-compose-ui` |
| `flowspeed-compose-ui` | Compose, Decompose, navigation, state wiring | Không chứa OS lifecycle hoặc engine semantics |
| `flowspeed-download-engine` | HTTP/HLS, retry, range, resume, persistence, queue | Không xử lý browser trust boundary hoặc native lifecycle |
| `flowspeed-gradle` | Build, test task, lint, CI parity, dependency setup | Đọc task/workflow hiện tại; không giữ CI matrix dễ stale |
| `flowspeed-integration-security` | Extension, localhost API, CORS, URL/filename trust | Kết thúc tại request đã được validate |
| `flowspeed-release` | Version, package, signing, artifact, publish | Đọc Gradle/workflow làm source of truth; publish cần xác nhận |
| `flowspeed-testing` | Behavioral test, deterministic fake, smoke/architecture test | Test theo rủi ro, không chase coverage % |
| `flowspeed-platform-runtime` | Android service/permission/boot; Desktop tray/notification/single-instance | OS API không leak vào `commonMain`; lifecycle phải idempotent |
| `flowspeed-architecture` | Module boundary, KMP source set, DI split, dependency exposure | Một ownership boundary mỗi patch; test/measurement trước refactor rủi ro |

Skill files nằm tại `.claude/skills/flowspeed-*/SKILL.md`.

### 12.3. Quy tắc chuẩn hóa

- Frontmatter chỉ gồm `name` và `description`; description nêu rõ trigger.
- Một skill tương ứng một domain có công việc lặp lại và invariant riêng.
- Cross-reference skill khác tại điểm handoff, không lặp nguyên checklist.
- Không lưu số dòng, danh sách module đầy đủ, CI job, artifact name hoặc trạng thái roadmap khi source đã định nghĩa chúng.
- Không tạo skill riêng cho i18n, performance, installer, browser extension hoặc storage lúc này: phạm vi quá hẹp hoặc đã thuộc skill hiện có.
- Verification dùng task nhỏ nhất chứng minh thay đổi, sau đó compile consumer bị ảnh hưởng.
- Skill kiến trúc phải giữ nguyên quyết định tại mục 10: test trước refactor, composition thay vì split theo line count, đo trước tối ưu và tránh Clean Architecture toàn repo.

### 12.4. Kết quả kiểm tra

- Có 9 skill, tên duy nhất và khớp tên thư mục.
- Frontmatter hợp lệ, code fence cân bằng, cross-reference không trỏ tới skill thiếu.
- Sáu skill cũ đã bỏ thông tin dễ stale và rút về workflow/invariant cốt lõi.
- Ba khoảng trống được bổ sung: testing, platform runtime và architecture.
- Thay đổi chỉ tác động tài liệu/hướng dẫn Claude Code; không đổi production symbol hoặc execution flow.

---

## Phụ lục: Công cụ hỗ trợ

### GitNexus MCP

Project đã được GitNexus index sẵn (13.859 symbols, 47.710 relationships, 300 execution flows). Theo AGENTS.md, trước khi sửa bất kỳ symbol nào:

1. `gitnexus_context({name: "..."})` để xem callers/callees.
2. `gitnexus_impact({target: "...", direction: "upstream"})` để đánh giá blast radius.
3. `gitnexus_detect_changes()` trước khi commit.

Vì khối lượng code lớn (~92k LOC), mỗi refactor nên chạy GitNexus trước để tránh regression.

### Tham chiếu file:line tổng hợp

| File | Dòng | Trạng thái | Vấn đề |
|---|---|---|---|
| `downloader/monitor/build.gradle.kts` | — | ✅ **ĐÃ FIX** | composeBase plugin removed |
| `downloader/monitor/.../DownloadMonitor.kt` | 28 | 🟡 P2 | Scope singleton — OK, thêm `Closeable` cho test |
| `downloader/monitor/.../DownloadMonitor.kt` | 254-258 | ✅ OK | intervalFlow(500) — hợp lý |
| `downloader/monitor/.../UiPart.kt` | 3 | 🟡 P1 | Vẫn còn compose.runtime dep — cân nhắc tách |
| `downloader/core/.../HttpDownloadJob.kt` | — | 🟡 P1 | 775 dòng — cần cách khác để tách |
| `downloader/core/.../db/DownloadListFileStorage.kt` | 22-34 | 🟡 P2 | O(n) I/O khi boot |
| `desktop/app/.../pages/home/sections/DownloadList.kt` | — | ✅ OK | Đã dùng Table (LazyColumn) |
| `android/app/.../pages/home/DownloadList.kt` | 59 | ✅ OK | Đã dùng LazyColumn |
| `android/app/.../di/` | — | ✅ **ĐÃ FIX** | Tách thành 9 module files |
| `android/app/.../util/AndroidGlobalExceptionHandler.kt` | 89 | ✅ **ĐÃ FIX** | MissingPermission suppressed |
| `android/app/.../util/FlowServiceNotificationManager.kt` | 310,340 | ✅ **ĐÃ FIX** | MissingPermission suppressed |
| `compositeBuilds/.../AndroidManifest.xml.hbs` | 3 | ✅ **ĐÃ FIX** | android:exported=true added |
| `shared/app/build.gradle.kts` | 28-36 | 🟡 Partial | Hầu hết vẫn api() — consumers import trực tiếp |
| `shared/app/.../util/DownloadSystem.kt` | 70 | 🟢 P2 | Không có UseCase abstraction |
| `shared/app/.../util/mvi/` | — | ✅ OK | ContainsEffects dùng ở 8+ components |
| `shared/app/.../util/BaseComponent.kt` | 11-24 | ✅ OK | Pattern tốt |
| `shared/config/` | — | ✅ OK | Đang dùng tích cực, JVM-only |
| `desktop/app/.../AppComponent.kt` | — | ⚡ Partial | 1.069 dòng theo source tái kiểm chứng ngày 2026-07-12 |
| `desktop/app/.../di/UiModule.kt` | 70-77 | 🟡 P2 | runBlocking — cân nhắc thay thế |
| `downloader/core/.../HttpResponseInfoTest.kt` | — | ✅ **ĐÃ THÊM** | HTTP response và Content-Range parser coverage |
| `downloader/core/.../HttpPartDownloaderTest.kt` | — | ✅ **ĐÃ THÊM** | Part response validation và connection cleanup |
| `downloader/core/.../HttpDownloadJobStateTest.kt` | — | ✅ **ĐÃ THÊM** | Persisted part restore/reset coverage |
| `tools/testing/` | — | ✅ **ĐÃ THÊM** | Cross-platform fast/full Gradle test runners |
| `.github/workflows/` | — | ✅ **ĐÃ THÊM** | build-check chạy core + utils tests; dependabot.yml |

---

**Người thực hiện khảo sát**: Claude Code (opencode)
**Ngày hoàn thành khảo sát gốc**: 2026-07-05
**Version khảo sát**: 1.5 (cập nhật HTTP safety net và trạng thái sản phẩm ngày 2026-07-19)

### Changelog
- **v1.5** (2026-07-19): Cập nhật test safety net và trạng thái sản phẩm:
  - Ghi nhận 11 test files và 89 core desktop test cases pass.
  - Thêm inventory behavior cho `HttpResponseInfo`, `HttpPartDownloader` và persisted `HttpDownloadJob` state.
  - Ghi nhận PowerShell/Linux/macOS test runners trong `tools/testing/`.
  - Cập nhật CI chạy cả `:downloader:core:desktopTest` và `:shared:utils:desktopTest`.
  - Phân biệt phần đã xác minh, full runner chưa chạy và lifecycle/retry coverage còn deferred.
  - Thêm đánh giá mức độ sản phẩm, khả năng hiện có, độ sẵn sàng và rủi ro còn lại.
- **v1.4** (2026-07-19): Chuẩn hóa Claude Code project skills:
  - Tinh gọn 6 skill hiện có, bỏ module/CI/artifact matrix dễ lỗi thời.
  - Thêm `flowspeed-testing`, `flowspeed-platform-runtime`, `flowspeed-architecture`.
  - Chuẩn hóa trigger, boundary, invariant, workflow, verification và cross-reference cho 9 skill.
  - Không tạo skill riêng cho phạm vi hẹp hoặc trùng: i18n, performance, installer, extension, storage.
- **v1.3** (2026-07-06): Cập nhật kết quả thực tế sau khi thực hiện toàn bộ batch refactor:
  - Đánh dấu các mục ĐÃ THỰC HIỆN (commits `bce62ca` → `9d0b0e1`)
  - Thêm section §8 tổng kết kết quả + lesson learned
  - Thêm section §9 priority table còn lại (thay thế §8 cũ)
  - Cập nhật trạng thái từng file trong reference table
  - Ghi nhận CI đã xanh (Compile ✅ + Android Lint ✅)
  - Ghi nhận lesson learned: extension functions / protected members, wildcard imports, grep strategy
- **v1.2** (2026-07-05): Deep review — xác minh từng đề xuất với source code thực tế:
  1. **BÁC BỎ mục 2.2**: LazyColumn đã dùng ở cả 2 platform.
  2. **HẠ MỨC mục 2.1**: DownloadMonitor scope là singleton by-design.
  3. **HẠ MỨC mục 2.3**: intervalFlow(500) chi phí negligible.
  4. **SỬA mục MVI**: ContainsEffects dùng tích cực ở 8+ components.
- **v1.1** (2026-07-05): Sửa 4 lỗi chính xác (mac-utils, shared:config, HomePage path, convention plugins).
