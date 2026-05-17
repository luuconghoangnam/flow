# Implementation Plan: Cyber-Industrial Layout Redesign

## Overview

This plan implements the home page layout redesign from sidebar + table view to top navigation bar + card grid view, following the Cyber-Industrial design system. Tasks are ordered to build shared components first, then platform-specific integrations, and finally cleanup.

## Tasks

- [ ] 1. Add `totalDiskUsageFlow` StateFlow to `BaseHomeComponent` that computes the sum of `contentLength` from `completedList`, emitting 0L when no completed downloads exist
  - [ ] 1.1 Add the flow definition using `completedList.map { list -> list.sumOf { it.contentLength } }.stateIn(scope, SharingStarted.Eagerly, 0L)`
  - [ ] 1.2 Verify the flow is accessible from platform HomeComponents

- [ ] 2. Create `CyberGridBackground` component at `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/CyberGridBackground.kt`
  - [ ] 2.1 Implement Canvas-based grid pattern with subtle lines (24dp step, very low opacity white)
  - [ ] 2.2 Use `Modifier.fillMaxSize()` to draw behind content

- [ ] 3. Create `CyberNavigationBar` component at `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/CyberNavigationBar.kt`
  - [ ] 3.1 Implement horizontal tab row with "All" tab plus category tabs from CategoryManager
  - [ ] 3.2 Style active tab with #E64A00 underline indicator and primary color text
  - [ ] 3.3 Use JetBrains Mono font for tab labels with RectangleShape tab indicators
  - [ ] 3.4 Support horizontal scrolling on Android for overflow tabs

- [ ] 4. Create `DashboardStatsHeader` component at `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/DashboardStatsHeader.kt`
  - [ ] 4.1 Implement three stat boxes in a horizontal Row: total speed, active count, disk usage
  - [ ] 4.2 Style stat boxes with `myColors.surface` background, sharp corners, 1dp border
  - [ ] 4.3 Use JetBrains Mono font for stat values, display "0" / "0 B/s" when no active downloads

- [ ] 5. Create `StatusFilterRow` component at `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/StatusFilterRow.kt`
  - [ ] 5.1 Implement horizontal row of filter chips for All, Downloading, Paused, Finished, Error
  - [ ] 5.2 Style active chip with `myColors.primary` background, inactive with `myColors.surface` background
  - [ ] 5.3 Use RectangleShape for chip shapes and monospace font for labels

- [ ] 6. Create `DownloadCard` component at `shared/app/src/commonMain/kotlin/com/flowspeed/link/shared/ui/widget/DownloadCard.kt`
  - [ ] 6.1 Implement card layout: checkbox + filename (truncated), category label, progress bar, speed/time/size row, status
  - [ ] 6.2 Style with RectangleShape, `myColors.surface` background, 1dp border, `myColors.primary` progress bar
  - [ ] 6.3 Use JetBrains Mono font for speed, size, and time remaining values
  - [ ] 6.4 Show selected state with `myColors.primary` border and subtle background tint
  - [ ] 6.5 Conditionally show speed and time remaining only for active downloads
  - [ ] 6.6 Display status text with appropriate color (success for completed, error for failed, warning for paused)

- [ ] 7. Create Desktop `CardGrid` component at `desktop/app/src/main/kotlin/com/flowspeed/link/desktop/pages/home/sections/CardGrid.kt`
  - [ ] 7.1 Implement `LazyVerticalGrid` with `GridCells.Adaptive(minSize = 280.dp)`
  - [ ] 7.2 Wire up Ctrl+click for multi-select, Shift+click for range select
  - [ ] 7.3 Wire up double-click to open file or show properties, right-click for context menu
  - [ ] 7.4 Support Ctrl+A to select all and Escape to deselect
  - [ ] 7.5 Support drag-and-drop of selected items (preserve existing behavior)
  - [ ] 7.6 Show empty state message when no downloads match filters

- [ ] 8. Create Android `CardGrid` component at `android/app/src/main/kotlin/com/flowspeed/link/android/pages/home/CardGrid.kt`
  - [ ] 8.1 Implement `LazyVerticalGrid` with `GridCells.Fixed(1)` for narrow screens and `GridCells.Fixed(2)` for wide (>600dp)
  - [ ] 8.2 Wire up tap for item click, long-press for context menu / selection mode
  - [ ] 8.3 Show empty state message when no downloads match filters

- [ ] 9. Rewrite Desktop `HomePage.kt` to use new layout
  - [ ] 9.1 Remove left sidebar Column (Categories, QueuesSection, Handle split pane, categoriesWidth state)
  - [ ] 9.2 Add CyberNavigationBar below title bar / menu bar area
  - [ ] 9.3 Add DashboardStatsHeader wired to `globalSpeedFlow`, `activeDownloadCountFlow`, `totalDiskUsageFlow`
  - [ ] 9.4 Add StatusFilterRow wired to `filterState.statusFilter`
  - [ ] 9.5 Replace DownloadList table with CardGrid component, add CyberGridBackground behind it
  - [ ] 9.6 Preserve search box, Add URL button, drag-and-drop overlay, dialog prompts, and menu bar merge behavior
  - [ ] 9.7 Remove Footer composable (stats now in DashboardStatsHeader)

- [ ] 10. Rewrite Android `HomePage.kt` to use new layout
  - [ ] 10.1 Add CyberNavigationBar below PageHeader
  - [ ] 10.2 Add DashboardStatsHeader and StatusFilterRow
  - [ ] 10.3 Replace existing DownloadList with CardGrid, add CyberGridBackground behind it
  - [ ] 10.4 Preserve bottom navigation, selection actions footer, enter-new-URL dialog, and all prompts

- [ ] 11. Clean up removed files and references
  - [ ] 11.1 Remove or deprecate `desktop/.../home/sections/DownloadList.kt`, `TableDownloadItem.kt`, `Filters.kt`
  - [ ] 11.2 Remove or deprecate `desktop/.../home/sections/category/` directory
  - [ ] 11.3 Update `HomePersistedState` to remove `categoriesWidth` if present
  - [ ] 11.4 Verify no compile errors after removals

- [ ] 12. Verify Cyber-Industrial styling across all new components
  - [ ] 12.1 Verify all components use RectangleShape, #E64A00 accent, #0A0A0A background
  - [ ] 12.2 Verify JetBrains Mono font for data values and grid background pattern renders
  - [ ] 12.3 Verify high-contrast text colors and consistent styling on both platforms

## Task Dependency Graph

```json
{
  "waves": [
    [1, 2, 3, 5, 6],
    [4, 7, 8],
    [9, 10],
    [11, 12]
  ]
}
```

## Notes

- The existing `BaseHomeComponent` architecture and state management remain unchanged — this is purely a UI layer redesign
- `TableState` and `DownloadListCells` may still be used in queue detail views; only remove from home page usage
- The `HomePersistedState` should be updated to remove sidebar-related persisted state
- Keyboard shortcuts (Ctrl+A, Escape) must be re-wired to work with the grid layout in Task 7
- The Android bottom navigation bar is preserved as-is; only the content area above it changes
