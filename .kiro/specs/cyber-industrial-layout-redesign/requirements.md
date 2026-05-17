# Requirements Document

## Introduction

Redesign the home page layout of the FlowSpeed Link download manager (Kotlin Multiplatform Compose, Desktop + Android) to follow a "Cyber-Industrial" design system. The current layout uses a left sidebar with categories and a right-side table view of downloads, which is too similar to the original AB Download Manager project. The new layout replaces this with a top navigation bar, card-based grid view, dashboard stats header, and no sidebar — creating a visually distinct identity aligned with the Cyber-Industrial aesthetic.

## Glossary

- **Home_Page**: The main screen of the FlowSpeed Link application where users view and manage their downloads
- **Navigation_Bar**: A horizontal bar at the top of the Home_Page containing category tabs (All, Compressed, Videos, Music, Programs, Documents, Other)
- **Dashboard_Stats_Header**: A horizontal section below the Navigation_Bar displaying aggregate download statistics
- **Download_Card**: A card UI element representing a single download item, displayed in a grid layout
- **Card_Grid**: A responsive grid layout that arranges Download_Cards in rows and columns
- **Category_Tab**: A clickable tab within the Navigation_Bar that filters downloads by file type category
- **Status_Filter**: A secondary filter mechanism that filters downloads by their current status (All, Downloading, Paused, Finished, Error)
- **Desktop_App**: The JVM Desktop target of the Kotlin Multiplatform application using Compose for Desktop
- **Android_App**: The Android target of the Kotlin Multiplatform application using Jetpack Compose
- **Cyber_Industrial_Theme**: The design system characterized by sharp corners, orange accent (#E64A00), dark background (#0A0A0A), monospace fonts, and grid background patterns

## Requirements

### Requirement 1: Remove Left Sidebar

**User Story:** As a user, I want the left sidebar removed from the Home_Page, so that the layout is visually distinct from the original project and provides more horizontal space for download content.

#### Acceptance Criteria

1. THE Home_Page SHALL NOT render a vertical sidebar panel for category navigation on Desktop_App
2. THE Home_Page SHALL NOT render a vertical sidebar panel for category navigation on Android_App
3. THE Home_Page SHALL use the full window width for download content display

### Requirement 2: Top Navigation Bar with Category Tabs

**User Story:** As a user, I want categories displayed as horizontal tabs at the top of the Home_Page, so that I can quickly filter downloads by file type without a sidebar.

#### Acceptance Criteria

1. THE Navigation_Bar SHALL display Category_Tabs horizontally across the top of the Home_Page below the window title bar
2. THE Navigation_Bar SHALL include tabs for: All, Compressed, Videos, Music, Programs, Documents, and Other categories
3. WHEN a user selects a Category_Tab, THE Home_Page SHALL filter the displayed downloads to show only items matching that category
4. THE Navigation_Bar SHALL visually indicate the currently active Category_Tab using the primary accent color (#E64A00)
5. THE Navigation_Bar SHALL use monospace font styling consistent with the Cyber_Industrial_Theme
6. THE Navigation_Bar SHALL remain fixed at the top of the Home_Page and not scroll with content

### Requirement 3: Dashboard Stats Header

**User Story:** As a user, I want to see aggregate download statistics at a glance, so that I can monitor overall download activity without inspecting individual items.

#### Acceptance Criteria

1. THE Dashboard_Stats_Header SHALL display the current total download speed across all active downloads
2. THE Dashboard_Stats_Header SHALL display the count of currently active (downloading) items
3. THE Dashboard_Stats_Header SHALL display the total disk usage consumed by completed downloads
4. THE Dashboard_Stats_Header SHALL be positioned between the Navigation_Bar and the Card_Grid
5. WHEN no downloads are active, THE Dashboard_Stats_Header SHALL display zero values for speed and active count
6. THE Dashboard_Stats_Header SHALL update speed and active count values in real-time as download states change
7. THE Dashboard_Stats_Header SHALL use the Cyber_Industrial_Theme styling with sharp corners and high-contrast text

### Requirement 4: Card-Based Grid Layout

**User Story:** As a user, I want downloads displayed as cards in a grid layout, so that I can visually scan my downloads with richer per-item information than a table row provides.

#### Acceptance Criteria

1. THE Card_Grid SHALL display each download as a Download_Card arranged in a responsive grid
2. WHEN the window is resized on Desktop_App, THE Card_Grid SHALL adjust the number of columns to fit the available width
3. THE Card_Grid SHALL display a minimum of 2 columns and a maximum of 6 columns on Desktop_App
4. THE Card_Grid SHALL display 1 column on narrow screens and 2 columns on wide screens on Android_App
5. THE Card_Grid SHALL support vertical scrolling when downloads exceed the visible area
6. THE Card_Grid SHALL replace the existing table-based download list on both Desktop_App and Android_App

### Requirement 5: Download Card Content

**User Story:** As a user, I want each download card to show essential information including progress, filename, and speed, so that I can monitor individual downloads at a glance.

#### Acceptance Criteria

1. THE Download_Card SHALL display the download filename with text truncation for long names
2. THE Download_Card SHALL display a horizontal progress bar showing download completion percentage
3. WHILE a download is active, THE Download_Card SHALL display the current download speed
4. WHILE a download is active, THE Download_Card SHALL display the estimated time remaining
5. THE Download_Card SHALL display the total file size
6. THE Download_Card SHALL display the current download status (Downloading, Paused, Completed, Error)
7. THE Download_Card SHALL use sharp corners with zero border radius consistent with the Cyber_Industrial_Theme
8. WHEN a user right-clicks a Download_Card on Desktop_App, THE Home_Page SHALL display a context menu with download actions
9. WHEN a user long-presses a Download_Card on Android_App, THE Home_Page SHALL display a context menu with download actions
10. WHEN a user double-clicks a Download_Card on Desktop_App, THE Home_Page SHALL open the completed file or show download properties

### Requirement 6: Status Filter

**User Story:** As a user, I want to filter downloads by status in addition to category, so that I can quickly find active, paused, or completed downloads.

#### Acceptance Criteria

1. THE Status_Filter SHALL provide filter options for: All, Downloading, Paused, Finished, and Error statuses
2. THE Status_Filter SHALL be displayed as a secondary row of filter chips or tabs below the Navigation_Bar or within the Dashboard_Stats_Header area
3. WHEN a user selects a status filter, THE Card_Grid SHALL display only downloads matching both the active Category_Tab and the selected Status_Filter
4. THE Status_Filter SHALL visually indicate the currently active filter option

### Requirement 7: Cyber-Industrial Visual Styling

**User Story:** As a user, I want the Home_Page to have a distinct Cyber-Industrial aesthetic, so that the application has a unique visual identity.

#### Acceptance Criteria

1. THE Home_Page SHALL use zero border radius on all UI elements including cards, buttons, tabs, and containers
2. THE Home_Page SHALL use the primary accent color #E64A00 for active states, selection indicators, and progress bars
3. THE Home_Page SHALL use the background color #0A0A0A for the main content area in dark mode
4. THE Home_Page SHALL use monospace font (JetBrains Mono) for data values such as speed, file size, and time remaining
5. THE Home_Page SHALL render a subtle grid background pattern behind the Card_Grid area for visual depth
6. THE Home_Page SHALL use high-contrast text colors (#E5E7EB on dark backgrounds) for readability

### Requirement 8: Desktop Menu Bar Retention

**User Story:** As a developer, I want the top menu bar (File, Tasks, Tools, Help) to remain functional on Desktop_App, so that existing functionality is preserved during the layout redesign.

#### Acceptance Criteria

1. THE Desktop_App SHALL retain the existing menu bar with File, Tasks, Tools, and Help menus
2. THE menu bar SHALL be positioned above the Navigation_Bar in the window title bar area
3. WHEN the window is wide enough to merge the menu bar with the title bar, THE Desktop_App SHALL continue to merge them as in the current implementation

### Requirement 9: Download Selection and Bulk Actions

**User Story:** As a user, I want to select multiple download cards and perform bulk actions, so that I can manage many downloads efficiently.

#### Acceptance Criteria

1. THE Download_Card SHALL support selection via a checkbox or visual selection indicator
2. WHEN a user holds Ctrl and clicks Download_Cards on Desktop_App, THE Home_Page SHALL add each clicked card to the current selection
3. WHEN a user holds Shift and clicks a Download_Card on Desktop_App, THE Home_Page SHALL select all cards between the last selected card and the clicked card
4. WHEN one or more Download_Cards are selected, THE Home_Page SHALL display available bulk actions (Resume, Pause, Delete, Move to Category)
5. THE Home_Page SHALL support select-all functionality via Ctrl+A keyboard shortcut on Desktop_App

### Requirement 10: Search Functionality

**User Story:** As a user, I want to search for downloads by filename, so that I can quickly locate specific downloads in a large list.

#### Acceptance Criteria

1. THE Home_Page SHALL provide a search input field accessible from the top area of the page
2. WHEN a user types in the search field, THE Card_Grid SHALL filter downloads to show only items whose filename contains the search text
3. THE search filter SHALL work in combination with the active Category_Tab and Status_Filter
4. THE search input field SHALL use Cyber_Industrial_Theme styling with sharp corners and monospace font
