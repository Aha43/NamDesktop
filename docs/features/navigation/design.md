# Feature: Navigation Quality

Status: **sprint planned** — design decisions resolved 2026-06-04. Dev chat start point: #336 (re-click fix), then #337 (back button).

## Motivation

NamDesktop navigation is already strong: breadcrumb, inline project links from action panels, ActionDialog "Open project" link, Mission Control card navigation. Two gaps surfaced from real use:

1. **Stuck nav selection** — clicking an already-selected nav item does nothing, because the JList fires no event when selection hasn't changed. Workaround: click another item, then click back. Irritating in the 10% of cases where it matters.

2. **No back navigation** — once you drill into a workbench (or open Help), there is no back arrow. The breadcrumb partially covers this within the workbench but not across the full nav stack.

## Issues

| # | Title |
|---|---|
| #336 | Re-click nav item always navigates to section root |
| #337 | Back navigation button and Cmd+[ keyboard shortcut |

## Design decisions

### Re-click fix

Add a `mouseClicked` listener to the nav panel in addition to the existing `ListSelectionListener`. On every click, fire the navigate action unconditionally — do not rely on selection change. If the new location equals the current location (same panel, same root), the navigate call is still made but the history stack (see below) treats it as a no-op (no duplicate push).

### NavigationLocation

A value type capturing where the user is. Minimum subtypes needed:

| Subtype | Fields | When used |
|---|---|---|
| `PanelLocation` | `NavItem panel` | Simple panels: Inbox, Projects, NextActions, Backlog, Context, Done, Search, MissionControl |
| `WorkbenchLocation` | `UUID projectId` | Project Workbench open on a specific project |
| `SavedViewLocation` | `String viewName` | A named saved view panel |

Help is **outside** navigation history — opening Help does not push a location, and Back does not return to Help.

### NavigationHistory

- `ArrayDeque<NavigationLocation>` capped at 20 entries
- Lives in `MainFrame` (or a thin `NavigationController` it owns)
- Push rule: before any navigation, if `newLocation != currentLocation`, push `currentLocation`. Same-location navigations (re-click fix) do not push.
- `back()`: pop the stack; if the popped location refers to a deleted node, pop again (up to 5 attempts); if nothing valid found, navigate to the nav panel root for the last popped panel type

### Back button UI

- Toolbar button: left-arrow icon (`arrow-left` from Tabler Icons); grayed/disabled when history is empty
- Keyboard shortcut: `Cmd+[`
- No forward button — not now, possibly never

### Deleted node handling

Silent skip — pop again until a valid location is found or the stack is exhausted. No toast. Landing at a section root is acceptable if all history entries are stale.

## Navigation events that push history

All navigation currently funnels through `MainFrame` panel-switching. History push should happen at that central point, not in each caller. Actions that trigger a push:

- Nav panel item click (any item, any panel)
- Opening a project workbench (from ProjectsPanel, NextActionsPanel, BacklogPanel, ContextPanel, SavedViewPanel, MissionControlPanel)
- Breadcrumb navigation within workbench
- ActionDialog "Open project" link

## Out of scope

- Forward button
- Help in navigation history
- Keyboard shortcut for Forward
- Persisting history across sessions
