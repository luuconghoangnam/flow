# Design Document: Cyber-Industrial Layout Redesign

## Overview

This design describes the implementation plan for replacing the current sidebar + table layout with a top navigation bar + card grid layout following the Cyber-Industrial design system. The redesign affects both Desktop and Android targets of the Kotlin Multiplatform Compose application.

## Components and Interfaces

### Shared Components (commonMain)

| Component | Interface | Purpose |
|-----------|-----------|---------|
| `CyberNavigationBar` | `(categories, selectedCategory, onCategorySelected, modifier)` | Horizontal category tab bar |
| `DashboardStatsHeader` | `(totalSpeed, activeCount, totalDiskUsage, modifier)` | Aggregate stats display |
| `StatusFilterRow` | `(currentFilter, onFilterSelected, modifier)` | Status filter chip row |
| `DownloadCard` | `(item, isSelected, category, fileIconProvider, onSelectionChange, onClick, onDoubleClick, onContextMenu, modifier)` | Individual download card |
| `CyberGridBackground` | `(modifier)` | Subtle grid pattern canvas |

### Platform-Specific Components

| Component | Platform | Purpose |
|-----------|----------|---------|
| `CardGrid` (Desktop) | Desktop | LazyVerticalGrid with keyboard interactions (Ctrl+click, Shift+click, double-click) |
| `CardGrid` (Android) | Android | LazyVerticalGrid with touch interactions (tap, long-press) |

### Interfaces

```kotlin
// No new interfaces needed - existing BaseHomeComponent API is sufficient
// New state flow added to BaseHomeComponent:
val totalDiskUsageFlow: StateFlow<Long>
```

## Data Models

### Existing Models (Unchanged)

- `IDownloadItemState` — represents a download item (active or completed)
- `ProcessingDownloadItemState` — active download with speed, progress, time remaining
- `CompletedDownloadItemState` — finished download
- `Category` — file type category with name, icon, file extensions
- `FilterState` — holds current text search, category filter, status filter, queue filter
- `DownloadStatusCategoryFilter` — enum of status filters (All, Downloading, Paused, Finished, Error)

### New Computed State

```kotlin
// Added to BaseHomeComponent
val totalDiskUsageFlow: StateFlow<Long> = completedList.map { list ->
    list.sumOf { it.contentLength }
}.stateIn(scope, SharingStarted.Eagerly, 0L)
```

### Grid Column Calculation

```kotlin
// Desktop: adaptive grid with min card width of 280dp
// Results in 2-6 columns for window widths 560dp-1680dp
GridCells.Adaptive(minSize = 280.dp)

// Android: fixed based on screen width
if (screenWidth > 600.dp) GridCells.Fixed(2) else GridCells.Fixed(1)
```

## Architecture

### Component Structure (Unchanged)

The existing component architecture (`BaseHomeComponent` → platform-specific `HomeComponent`) remains unchanged. The redesign is purely a UI/composable layer change. All state management, filtering logic, and download system integration stay as-is.

```
BaseHomeComponent (shared)
├── filterState: FilterState (category, status, search text)
├── downloadList: StateFlow<List<IDownloadItemState>>
├── activeDownloadCountFlow: StateFlow<Int>
├── globalSpeedFlow: Flow<Long>
├── selectionList: StateFlow<List<Long>>
├── categoryManager: CategoryManager
└── Platform HomeComponent (desktop/android)
```

### New Composable Hierarchy

#### Desktop (`HomePage.kt` rewrite)

```
HomePage
├── Column (full page)
│   ├── [Title Bar / Menu Bar] (existing, unchanged)
│   ├── NavigationBar (NEW - horizontal category tabs)
│   ├── DashboardStatsHeader (NEW - speed, active count, disk usage)
│   ├── StatusFilterRow (NEW - All/Downloading/Paused/Finished/Error chips)
│   └── CardGrid (NEW - LazyVerticalGrid of DownloadCards)
│       └── DownloadCard (NEW - per-item card)
└── [Overlays: dialogs, drag widget, notifications]
```

#### Android (`HomePage.kt` rewrite)

```
HomePage
├── Column (full page)
│   ├── PageHeader (existing app icon + title)
│   ├── NavigationBar (NEW - horizontal scrollable category tabs)
│   ├── DashboardStatsHeader (NEW - speed, active count, disk usage)
│   ├── StatusFilterRow (NEW - horizontal scrollable filter chips)
│   └── CardGrid (NEW - LazyVerticalGrid of DownloadCards)
│       └── DownloadCard (NEW - per-item card)
└── Footer (existing - bottom navigation / selection actions)
```

## Detailed Design

### 1. Navigation Bar Component

**Location:** `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/NavigationBar.kt`

A shared composable that renders category tabs horizontally.

```kotlin
@Composable
fun CyberNavigationBar(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    modifier: Modifier = Modifier
)
```

**Behavior:**
- Renders an "All" tab plus one tab per category from `CategoryManager.categoriesFlow`
- Active tab gets `#E64A00` underline/background indicator
- Uses `JetBrains Mono` font family
- On Desktop: all tabs visible, horizontally arranged with equal spacing
- On Android: horizontally scrollable `ScrollableTabRow`
- Tab selection calls `component.onCategoryFilterChange(statusFilter, category)`

**Styling:**
- Background: `myColors.surface` (#1E1E1E in dark mode)
- Text: `myColors.onSurface` with active tab using `myColors.primary`
- No rounded corners on tab indicators
- Bottom border: 2dp solid `myColors.primary` on active tab

### 2. Dashboard Stats Header Component

**Location:** `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/DashboardStatsHeader.kt`

```kotlin
@Composable
fun DashboardStatsHeader(
    totalSpeed: Long,
    activeCount: Int,
    totalDiskUsage: Long,
    modifier: Modifier = Modifier
)
```

**Data Sources (already available in BaseHomeComponent):**
- `totalSpeed`: from `globalSpeedFlow` (sum of active download speeds)
- `activeCount`: from `activeDownloadCountFlow`
- `totalDiskUsage`: NEW - computed from `completedList` by summing `contentLength`

**New state in BaseHomeComponent:**
```kotlin
val totalDiskUsageFlow: StateFlow<Long> = completedList.map { list ->
    list.sumOf { it.contentLength }
}.stateIn(scope, SharingStarted.Eagerly, 0L)
```

**Layout:** Three stat cards in a horizontal `Row`, each showing:
- Icon + label (small, muted text)
- Value (large, monospace, high-contrast)

**Styling:**
- Each stat in a box with `myColors.surface` background
- Sharp corners (RectangleShape)
- 1dp border using `myColors.onBackground / 10`
- Values use `JetBrains Mono` font

### 3. Status Filter Row Component

**Location:** `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/StatusFilterRow.kt`

```kotlin
@Composable
fun StatusFilterRow(
    currentFilter: DownloadStatusCategoryFilter,
    onFilterSelected: (DownloadStatusCategoryFilter) -> Unit,
    modifier: Modifier = Modifier
)
```

**Behavior:**
- Renders filter chips for each `DefinedStatusCategories` value
- Active chip uses `myColors.primary` background with `myColors.onPrimary` text
- Inactive chips use `myColors.surface` background with `myColors.onSurface` text
- Selection calls existing `filterState.statusFilter = selected`

**Styling:**
- Chips have zero border radius (RectangleShape)
- Horizontal arrangement with 8dp spacing
- Monospace font for chip labels

### 4. Card Grid Component

**Location (Desktop):** `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/pages/home/sections/CardGrid.kt`
**Location (Android):** `android/app/src/main/kotlin/com/flowspeed/link/android/pages/home/CardGrid.kt`

```kotlin
@Composable
fun CardGrid(
    downloadList: List<IDownloadItemState>,
    selectionList: List<Long>,
    onItemSelectionChange: (Long, Boolean) -> Unit,
    onRequestOpenOption: (IDownloadItemState) -> Unit,
    onRequestOpenDownload: (Long) -> Unit,
    onNewSelection: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
)
```

**Implementation:**
- Uses `LazyVerticalGrid` with `GridCells.Adaptive(minSize = 280.dp)` on Desktop
- Uses `GridCells.Fixed(1)` on narrow Android, `GridCells.Fixed(2)` on wide Android (>600dp)
- Column count naturally constrained to 2-6 on Desktop by min card width of 280dp in typical window sizes (560dp-1680dp)
- Supports Ctrl+click, Shift+click, and double-click interactions (Desktop)
- Supports long-press for context menu (Android)
- Background: subtle grid pattern using `CyberGridBackground` canvas drawing

**Grid Background Pattern:**
```kotlin
@Composable
fun CyberGridBackground(modifier: Modifier) {
    Canvas(modifier) {
        // Draw subtle grid lines
        val step = 24.dp.toPx()
        val lineColor = Color(0x0AFFFFFF) // very subtle white lines
        // horizontal and vertical lines at `step` intervals
    }
}
```

### 5. Download Card Component

**Location:** `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/DownloadCard.kt`

```kotlin
@Composable
fun DownloadCard(
    item: IDownloadItemState,
    isSelected: Boolean,
    category: Category?,
    fileIconProvider: FileIconProvider,
    onSelectionChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onContextMenu: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Card Layout:**
```
┌─────────────────────────────────┐
│ [✓] filename.ext            ... │  ← checkbox + name + overflow menu
│ Category: Videos                │  ← category label (muted)
│                                 │
│ ████████████░░░░░░░░  67%       │  ← progress bar + percentage
│                                 │
│ 2.4 MB/s    12:34    156 MB     │  ← speed, time left, size
│ Status: Downloading             │  ← status text with color
└─────────────────────────────────┘
```

**Styling:**
- Shape: `RectangleShape` (zero border radius)
- Background: `myColors.surface`
- Border: 1dp `myColors.onBackground / 10`, becomes `myColors.primary` when selected
- Progress bar: full-width, 4dp height, `myColors.primary` fill, `myColors.background` track
- Font: `JetBrains Mono` for speed, size, time values
- Selection: border changes to `myColors.primary`, subtle background tint

### 6. Removed Components

The following are removed from the Desktop HomePage:
- `Categories()` composable (left sidebar category tree)
- `QueuesSection()` in sidebar
- `Handle()` split pane divider
- `categoriesWidth` state and resize logic
- `DownloadList` table-based composable (replaced by `CardGrid`)
- `DownloadListCells` enum (table columns no longer needed)

The following are removed from the Android HomePage:
- `SimplePager` category pager (replaced by `NavigationBar` tabs)

### 7. Files Modified

| File | Change |
|------|--------|
| `desktop/.../home/HomePage.kt` | Major rewrite: remove sidebar, add NavigationBar, DashboardStatsHeader, StatusFilterRow, CardGrid |
| `android/.../home/HomePage.kt` | Major rewrite: add NavigationBar, DashboardStatsHeader, StatusFilterRow, CardGrid |
| `android/.../home/DownloadList.kt` | Remove or replace with CardGrid usage |
| `shared/.../home/BaseHomeComponent.kt` | Add `totalDiskUsageFlow` |
| `desktop/.../home/sections/DownloadList.kt` | Remove (replaced by CardGrid) |
| `desktop/.../home/sections/TableDownloadItem.kt` | Remove (replaced by DownloadCard) |
| `desktop/.../home/sections/Filters.kt` | Remove (replaced by StatusFilterRow) |

### 8. New Files

| File | Purpose |
|------|---------|
| `shared/.../ui/widget/CyberNavigationBar.kt` | Shared horizontal category tab bar |
| `shared/.../ui/widget/DashboardStatsHeader.kt` | Shared stats header component |
| `shared/.../ui/widget/StatusFilterRow.kt` | Shared status filter chips |
| `shared/.../ui/widget/DownloadCard.kt` | Shared download card component |
| `shared/.../ui/widget/CyberGridBackground.kt` | Grid pattern background canvas |
| `desktop/.../home/sections/CardGrid.kt` | Desktop-specific grid with keyboard interactions |
| `android/.../home/CardGrid.kt` | Android-specific grid with touch interactions |

## Error Handling

- **Empty state**: When no downloads match the current filters, the Card_Grid displays a centered "No downloads" message (same as current table empty state)
- **Missing category data**: If `CategoryManager` returns an empty category list, the Navigation_Bar shows only the "All" tab
- **Invalid disk usage**: If `contentLength` is negative or zero for a completed download, it is excluded from the disk usage sum
- **Grid layout edge cases**: If window width is too narrow for 2 columns (< 560dp on Desktop), the grid falls back to a single column

## Correctness Properties

### Property 1: Category Filter Correctness
For any selected category tab, the displayed download list contains only items that belong to that category (or all items when "All" is selected).

**Validates: Requirements 2.3**

### Property 2: Status Filter Correctness
For any selected status filter, the displayed download list contains only items matching that status. Combined with category filter, the result is the intersection of both filters.

**Validates: Requirements 6.3**

### Property 3: Search Filter Correctness
For any search text, the displayed download list contains only items whose filename contains the search text (case-insensitive). Combined with category and status filters, the result is the intersection of all three filters.

**Validates: Requirements 10.2, 10.3**

### Property 4: Grid Column Count Bounds (Desktop)
For any window width, the card grid displays between 2 and 6 columns inclusive.

**Validates: Requirements 4.2, 4.3**

### Property 5: Dashboard Stats Accuracy
The total disk usage displayed equals the sum of `contentLength` for all completed downloads. The active count equals the number of downloads with active status. The total speed equals the sum of speeds of all active downloads.

**Validates: Requirements 3.1, 3.2, 3.3**

### Property 6: Selection State Consistency
The selection state is preserved correctly across filter changes — selected items that are no longer visible are removed from the selection list (already implemented in BaseHomeComponent).

**Validates: Requirements 9.1, 9.2, 9.3**

## Testing Strategy

- **Unit tests**: Verify `totalDiskUsageFlow` computation, column count calculation logic
- **UI tests**: Verify card rendering with various download states, navigation bar tab switching, stats header display
- **Integration tests**: Verify end-to-end filter combinations (category + status + search)

## Migration Notes

- The `TableState` and `DownloadListCells` infrastructure used by the desktop table view will no longer be needed for the home page but should be preserved if used elsewhere (e.g., queue detail views)
- The `HomePersistedState` may need updating to remove `categoriesWidth` and add any new persisted preferences (e.g., preferred grid density)
- Existing keyboard shortcuts (Ctrl+A, Escape to deselect) must be re-wired to work with the grid layout
