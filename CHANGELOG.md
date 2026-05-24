# Changelog

All notable changes to NamDesktop will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

- `swingdemo` — reusable library for driving Java Swing apps from a JSON script (`ActionHandler`, `RefreshBus`, `DemoStep`, `ScriptRunner`). Closes #168.
- Demo script (`demo.json`) bundled in the JAR builds a realistic GTD workspace live — projects, sub-projects, actions, tags, and saved views. Triggered via File → Run Demo… or `--demo` CLI flag. Step descriptions appear in a status bar at the bottom of the window. Closes #170.
- `NamDemoWiring` registers NamDesktop action handlers on a `ScriptRunner` (`addProject`, `addSubProject`, `addAction`, `addNextAction`, `addInboxItem`, `markNext`, `markDone`, `markBacklog`, `addTag`, `createSavedView`). `MainFrame.refreshAll()` added as the `RefreshBus` target. Closes #169.

- Status toggle in ActionDialog replaced with Backlog / Next / Done radio group — all three statuses reachable without closing the dialog. Closes #162.

- Actions inherit tags from ancestor projects at query time: filtering the Context view by a project's tag now surfaces all NEXT actions in that project and its sub-projects, even when the actions carry no tags themselves. Closes #159.
- Context lens and saved views now show all active (non-done) matching actions, not just NEXT — making the Context view a true inventory. Saved views gain a "Next actions only" toggle (default off) for users who want a tighter committed-next list. Closes #161.

- Quick rename button (cursor-text icon) in sub-project section action bar — opens a pre-filled input dialog, no full edit dialog needed. Closes #155.
- Quick description button (notes icon) in sub-project section action bar — opens a textarea dialog pre-filled with current description. Closes #156.
- Full Edit button moved to far right of sub-project action bar, signalling it is the "do all" option.
- Rename, Description, and Edit buttons also available in the current project's own action bar, consistent with sub-project sections. Closes #157.

- Inbox items appearing in the Backlog panel are rendered in italic to signal they are unprocessed captures; double-clicking still opens ActionDialog for processing. Closes #149.
- Sub-project section headers in the workbench are rendered bold+italic when the sub-project has its own sub-projects, signalling there is more depth to explore. Closes #151.
- Inline edit button (pencil) in each sub-project section's action bar — opens ProjectDialog directly without navigating in first. Closes #152.
- Inline edit button alongside each breadcrumb ancestor link — edit any ancestor project without losing your current position. Closes #153.
- `UiHelper.iconOnlyButton` factory method for buttons that must stay icon-only regardless of dense mode setting (used for compact inline contexts like breadcrumbs).
- Educational tooltips on hardcoded navigation entries (Inbox, Projects, Next Actions, Context, Backlog, Raw Tree) explaining their GTD role. Closes #146.
- Generated tooltips on saved view navigation entries showing the configured tag filter. Closes #147.
- Ordering Phase 2 — view-specific ordering in Next Actions and Backlog panels: up/down icon buttons in each toolbar reorder actions independently of the structural tree; order persists across sessions via `viewOrders` map; selection follows the moved item for frictionless repeated moves; `ViewOrderReconciler` drops removed items, preserves saved order, appends new items in live order. Closes #140, Closes #141, Closes #142.

### Fixed

- Delete button (trash icon) added to sub-project action bar in the workbench — confirms before deleting, shows a clear error if the project still has items inside. Full-edit button remains rightmost. Closes #166.
- Deleting a sub-project while viewing it in the workbench now navigates back to the parent project instead of leaving a blank screen; deleting a top-level project returns to the Projects list. Closes #165.
- "Make project" in ActionDialog no longer crashes when the action is inside a project; it now promotes the action to a sub-project in place instead of requiring it to be in the Next Actions area. Closes #163.
- Git sync push/pull buttons and menu items are now hidden in dev mode, preventing accidental overwrite of production data. Closes #150.
- GTD correctness: Next Actions and Backlog lenses now exclude projects — only actions belong in these views. Closes #145.

### Changed

- Setting "Always show status column" (default off) — hides the redundant Status column in Next Actions and Backlog panels; toggle takes effect immediately without restart. Closes #144.

- Ordering Phase 1 — up/down reordering throughout the app:
  - Workbench action lists: up/down icon buttons in each action bar; selection preserved across rebuild so repeated moves need no reselection; actions skip over sub-project siblings when moving. Closes #136.
  - Workbench sub-project sections: up/down icon buttons in each section header; sub-projects skip over action siblings when moving. Closes #137.
  - Raw tree: "Move up" / "Move down" in right-click context menu; greyed out for root and boundary positions. Closes #138.
  - `NamWorkspaceService.moveChildUp/Down` (general), `moveActionUp/Down` and `moveProjectUp/Down` (type-aware, skip same-kind siblings).

### Fixed

- Navigation panel selection colour flickered between focused and unfocused states whenever the workbench rebuilt. Fixed by pinning the cell renderer to `UIManager.getColor("List.selectionBackground")` regardless of focus state.

- Rich developer tooltips on raw tree nodes — hovering any node shows Type, Status, Tags, UUID, child count, and up to 50 characters of description. Closes #134.

- Icons and tooltips on all remaining toolbar/action buttons app-wide — `ActionDialog` (Make project → folder-plus, Move to backlog → archive, Open project → arrow-right), `ProjectDialog` (Convert to action → arrow-right, Save as Template → copy, Add sub-project → folder-plus), `InboxPanel` (Add item → plus), `ProjectsPanel` (Add project → folder-plus), `NodeDialog` status toggle (check icon), `ContextPanel` (Save as view → bookmark, Clear → eraser), `MainFrame` Exit button (logout icon). All buttons have descriptive tooltip text.

- "New project" button in `ProjectWorkbenchPanel` breadcrumb bar — creates a direct sub-project of the currently viewed project; tooltip names the target project.

### Changed

- `ProjectDialog` simplified to a pure metadata editor — actions table and child-management buttons (Add action, Add sub-project) removed since the workbench already provides that surface. Dialog height reduced from ~580 to ~350. Closes #131.

- Context panel Clear button now disabled when no tags are selected, consistent with Save as view and Add action buttons.

### Fixed

- Raw tree collapses to root after every mutation (add child, rename, mark done, delete) — expanded `TreePath`s are now snapshotted before model reload and restored after; for Add child the parent node is also expanded so the new child is immediately visible. Closes #133.

### Added

- `ProjectWorkbenchPanel` — action-forward project working surface in the ContentArea; breadcrumb navigation (Projects › Parent › Current), "This project" action list, one section per direct child project with its own action list, child project headers navigate in, breadcrumb navigates back up, "Edit project…" opens ProjectDialog for metadata; done actions shown gray; double-clicking an action opens ActionDialog. Closes #126.

- `TemplateNode` carries `project` flag so cloned template nodes correctly restore as projects or actions. Fixes template sub-project regression.

- `ProjectWorkbenchLens` — data projection for the Project Workbench: breadcrumb path, direct actions, one `ChildSection` per direct child project with its own direct actions; grandchild projects not expanded; done actions included. Closes #125.

- Sub-projects in `ProjectDialog` — "Add sub-project" button alongside "Add action"; sub-project nodes carry a `project = true` flag; double-clicking a child routes to `ProjectDialog` or `ActionDialog` based on the flag; section heading renamed to "Actions & sub-projects". Closes #123.

- Add project directly from Projects panel — "Add project" toolbar button creates a project without going through the inbox. Closes #122.

- Projects have no actionable status — status toggle button removed from `ProjectDialog`; projects are organizing containers, not actionable items. Closes #121.

- Project templates — create a named template from any existing project (captures the full subtree); apply a template when converting an inbox item to a project (template children are cloned under the new project, then the ProjectDialog opens immediately); manage templates (list and delete) via File → Templates…. Closes #116, #117, #118, #119.

- Push / Pull toolbar buttons — `cloud-upload.svg` and `cloud-download.svg` icons; only shown when sync is configured; use `UiHelper.iconButton()` so they participate in dense mode. Closes #115.

- Push / Pull workspace in File menu — only shown when a sync repo URL is configured; operations run on a background thread (UI stays responsive); success shows a confirmation dialog; pull reminds the user to restart to apply the updated workspace; errors surface a readable message. Closes #113.

- Sync repo URL in `AppSettings` (`syncRepoUrl` field, persisted to `settings.json`); sync section in `SettingsPanel` with URL text field (saves on Enter or focus-lost); `GitSyncService` constructed in `NamDesktopMain` from the configured URL and passed to `MainFrame`. Closes #112.

- `WorkspaceSyncService` interface and `GitSyncService` implementation — generic sync interface in `namdesktop.sync`; git-backed implementation uses `ProcessBuilder` to clone, commit, push and pull; fails fast with a clear message when not configured. Closes #111.

- All icon buttons refactored to `UiHelper.iconButton()` — `NextActionsPanel`, `BacklogPanel`, `ContextPanel`, `SavedViewPanel`, `ProjectDialog`, `NodeDialog`, `TagManagementDialog`, `MainFrame` toolbar; all participate in dense mode and always show a tooltip. Closes #109.

- Dense mode setting — `AppSettings.dense` boolean (default `false`); `UiHelper.iconButton(label, icon)` factory creates dense-aware buttons with mandatory tooltips; `UiHelper.applyDense(boolean)` walks all open windows and flips button labels live; "Dense mode" checkbox in `SettingsPanel`. Closes #108.

- `SettingsDialog` and File → Settings… menu item — settings reachable from the main window at any time. Closes #105.
- `SplashDialog` restructured with `JTabbedPane` — "Launch" tab (existing dev mode checkbox) and "Settings" tab (theme selector); theme change at splash applies before the main window opens. Closes #105.

- `SettingsPanel` — theme selector combo box (DARK/LIGHT); applies live via `FlatLaf.updateUI()` and `SwingUtilities.updateComponentTreeUI` on all open windows; persists to `~/.namdesktop/settings.json` immediately. Closes #104.

- `AppSettings` model and JSON persistence — settings stored in `~/.namdesktop/settings.json` separate from workspace data; initial field: `theme` (DARK/LIGHT, default DARK); missing or corrupt file falls back to defaults gracefully. Closes #103.
- `Theme` enum (`DARK`, `LIGHT`) in `namdesktop.app`.
- Theme applied from settings at startup in `NamDesktopMain` instead of hardcoded `FlatDarkLaf`.

- Editable title in `NodeDialog` — the title is now a text field; renaming an action or project is done in the same dialog as editing description and tags; blank title is rejected. Closes #101.

- Dialog toolbar icons: Delete button in `NodeDialog` (inherited by `ActionDialog` and `ProjectDialog`) → `trash.svg`; Rename… button in `TagManagementDialog` → `pencil.svg`; New tag… button → `plus.svg`. Closes #94.
- `trash.svg` and `pencil.svg` added to `src/icons/`; `scripts/download-icons.sh` updated.

- Icons on main toolbar: Search button → `search.svg`, Manage Tags… button → `tag.svg`. Closes #93.
- `search.svg` and `tag.svg` added to `src/icons/`; `scripts/download-icons.sh` updated with both names.

- `+` icon on "Add action" button in `ProjectDialog` — consistent with all other Add action buttons; reuses existing `plus.svg`. Closes #92.

- `"Ctrl+Enter to save"` tooltip on the description text area in `NodeDialog` — makes the keyboard shortcut discoverable on hover. Closes #95.

- Default button in `NodeDialog` — Enter triggers Save from any field; Ctrl+Enter triggers Save from within the description text area. Closes #91.
- Default button in `TagManagementDialog` — Enter closes the dialog. Closes #91.

- `+` icon on Add action buttons in `NextActionsPanel`, `BacklogPanel`, `ContextPanel`, and `SavedViewPanel` via `FlatSVGIcon` — icon adapts to dark theme automatically. Closes #89.

### Fixed

- `FlatSVGIcon` red-square error on all Add action buttons — `FlatSVGIcon(String, int, int)` uses FlatLaf's own classloader and cannot find resources from the app JAR; fixed by using `SomePanel.class.getResource("/icons/plus.svg")` (URL form) with `.derive(16, 16)` to keep the target size.

- `src/icons/plus.svg` — Tabler-style SVG icon with `currentColor` stroke for automatic dark-theme adaptation.
- `scripts/download-icons.sh` — fetches named Tabler Icons (MIT) into `src/icons/`; add icon names to the `ICONS` array to extend.
- `Makefile` copies `src/icons/` → `build/classes/icons/` after compile so icons are on the classpath. Closes #88.

- Raw Tree nav entry only shown when dev mode is active; `MainFrame` takes `boolean devMode` and builds nav entries dynamically. Closes #86.

- Dev mode uses `~/.namdesktop/dev/workspace.json`; normal mode unchanged at `~/.namdesktop/workspace.json`. Path selected in `NamDesktopMain` from the splash result — no other code changes needed. Closes #85.

- `SplashDialog` — shown on startup before the main window; displays app name and version; "Run in dev mode" checkbox pre-checked from `~/.namdesktop/.devmode` flag file; choice persisted on Launch; closing the splash exits the app. Closes #84.
- `[DEV]` appended to window title when dev mode is active.

- "Add action" button in `SavedViewPanel` header — creates a NEXT action pre-tagged with the view's tags; appears in the results immediately. Closes #82.

- "Add action" button in `ContextPanel` filter header — enabled when ≥1 tag is checked; creates a NEXT action pre-tagged with all checked tags; result appears immediately in the filtered list. Closes #81.
- `NamWorkspaceService.createNextAction(String, List<String>)` — tagged variant; `createActionWithStatus` extended to set tags before the single save.

- "Add action" toolbar button in `BacklogPanel` — creates a BACKLOG action directly; prompts for title, refreshes immediately. Closes #80.
- `NamWorkspaceService.createBacklogAction(String)` — shared helper `createActionWithStatus` keeps both methods DRY.

- "Add action" toolbar button in `NextActionsPanel` — creates a NEXT action directly without going through the inbox; prompts for title, refreshes immediately. Closes #79.
- `NamWorkspaceService.createNextAction(String)` — creates a node with status NEXT under the Actions area in one save.

- `SearchPanel` — text field with live keystroke updates; results table (Title, Type, Project); double-click opens `ActionDialog` or `ProjectDialog`; search field auto-focused on nav selection. Closes #77.

- `SearchResultRow(UUID id, String title, String type, String parentTitle, NodeStatus status)` record in `namdesktop.lens`.
- `SearchLens.search(NamWorkspace, String)` — case-insensitive substring match on title and tags; excludes structural and non-active nodes; results ordered Inbox → Action → Project → Backlog. Closes #76.

- "Manage Tags…" button added to toolbar; Exit button pushed to far right via `Box.createHorizontalGlue()`.
- Saved views now restored in the nav on startup — `MainFrame` calls `rebuildSavedViews` at construction time.

- Dynamic nav panel — saved views appear below a `JSeparator` divider after the standard entries; `NavigationPanel.rebuildSavedViews(List<SavedView>)` repopulates the section; divider rows are non-selectable. Closes #74.
- `SavedViewPanel` — shows `ContextLens` results pre-filtered to the saved view's tags; header with view name, tag list, and "Delete view" button (confirmation dialog); double-click opens `ActionDialog`; on delete navigates back to Context and rebuilds the nav.

- "Save as view…" button in `ContextPanel` filter header — enabled when ≥1 tag is checked; prompts for a name, calls `createSavedView`, shows an error dialog on blank or duplicate name. Closes #73.

- `SavedView(String name, List<String> tags)` record in `namdesktop.model`.
- `savedViews` field on `NamWorkspace` (defaults to empty list; null-safe setter).
- `NamWorkspaceService.createSavedView(String, List<String>)` — trims name, throws on blank or duplicate, saves once.
- `NamWorkspaceService.deleteSavedView(String)` — removes by name, no-op if not found, saves only if removed.
- `savedViews` persisted in `WorkspaceFile`; old files without the field deserialise cleanly. Closes #72.



- Tags column added to Next Actions, Backlog, and Projects panels, and to the ProjectDialog child action list. Closes #66, #69.
- Match count label and Clear button added to ContextPanel filter header. Closes #67, #68.
- `registeredTags` field on `NamWorkspace` — persists tags created up-front before they are applied to any node; `allTags()` merges registered tags with node tags.
- `NamWorkspaceService.registerTag(String)` — adds a tag to the registry (normalised, deduped); saves only if new.
- "New tag…" button in TagManagementDialog — creates a tag in the registry so it appears in autocomplete before any node uses it. Closes #70.

### Fixed

- `registeredTags` not persisted to `workspace.json` — tags created upfront via "New tag…" were lost on restart. Added `registeredTags` to `WorkspaceFile` DTO and wired load/save.
- `deleteTag` now also removes from `registeredTags`, so unused (registry-only) tags can actually be deleted. Confirmation wording when count is 0 changed to "Remove from tag list?" rather than the destructive node-removal wording.

- `NamWorkspaceService.renameTag(String, String)` — renames a tag across all nodes, deduplicates if target already present, saves once only if any node was changed.
- `NamWorkspaceService.deleteTag(String)` — removes a tag from all nodes, saves once only if any node was changed.
- `TagManagementDialog` — modal dialog (File → Manage Tags…) showing all tags with usage counts; Rename… and Delete buttons with confirmation. Closes #64.

- `TagsField` — custom `JTextField` subclass with autocomplete popup; filters existing tags from `allTags()` by the current token (substring match, case-insensitive), excludes tags already present in the field; keyboard navigation (↑↓ to move, Enter/Tab to select, Escape to dismiss). Closes #63.

### Fixed

- `NodeDialog` layout bug: tags row and `addBelowDescription` both used `BorderLayout.SOUTH` on the centre panel, causing subclasses (`ProjectDialog`, `ActionDialog`) to silently overwrite the tags row. Fixed by nesting description + tags in an inner panel, leaving `centre`'s SOUTH free for subclasses.



- `NamWorkspace.allTags()` — returns a sorted, deduplicated union of all tags across all nodes.
- `ContextLens` / `ContextItemRow` — filters NEXT actions by a required set of tags (AND semantics); excludes structural nodes; includes `parentTitle` and `tags` fields.
- `ContextPanel` — "Context" nav view with a wrapping checkbox panel (one per known tag) and a results table (Title, Project, Tags); no tags checked shows all NEXT actions; double-click opens `ActionDialog`. Closes #61.
- `List<String> tags` field on `NamNode` — defaults to empty list; `setTags(null)` safe (coerces to empty list); serialised automatically by Jackson, old files without `tags` deserialise cleanly.
- `NamWorkspaceService.addTag(UUID, String)` — normalises to lowercase/trimmed, deduplicates, saves.
- `NamWorkspaceService.removeTag(UUID, String)` — no-op if absent, saves only when changed.
- `NamWorkspaceService.updateTags(UUID, List<String>)` — bulk replace used by the UI on Save.
- Tags editor row in `NodeDialog` — comma-separated text field below the description area; saved together with the description on the Save button. Closes #60.



- `NamWorkspaceService.convertProjectToAction(UUID)` — demotes a leaf project back to an action; top-level projects move to the Actions area with status `NEXT`; sub-projects stay under their parent project and get status `NEXT`; throws `IllegalStateException` if the project has children.
- "Convert to action" button in `ProjectDialog` toolbar — converts the project and closes; shows an error if the project still has child actions. Closes #58.



- `ProjectDialog` "Add action" button — creates a child action directly under the project and refreshes the list immediately.
- `ProjectDialog` accepts an optional `initialSelection` UUID — scrolls to and selects that action row on open.
- `ActionDialog` context row — when an action belongs to a project, shows a "Project: [name]" label (tooltip shows breadcrumb path excluding root, e.g. `Projects > My Project`) and an "Open project" button that closes the action dialog and opens `ProjectDialog` with the action pre-selected. Closes #54.
- `NamWorkspace.getParent(UUID)` — finds the parent node of any child by scanning childId lists.
- `NamWorkspace.buildPath(UUID)` — returns the ordered list of nodes from root to the given node; used for breadcrumb display. Closes #55.
- "Project" column in Next Actions and Backlog tables — shows the parent node title for actions that belong to a project; blank for standalone actions. Closes #52.
- `ProjectDialog` action list upgraded from `JList<String>` to a two-column `JTable` (Title, Status); DONE rows rendered in grey matching the style of other panels. Closes #53.

### Fixed

- Parent panel now refreshes immediately after any dialog mutation (mark done/next, delete, move to backlog, make project, save description) — no longer requires navigating away and back.

### Added

- Menu bar with **File → Exit** and a top-level toolbar **Exit** button; placeholders for future menu/toolbar items.

### Changed

- Switched from `FlatLightLaf` to `FlatDarkLaf` — app now launches with the dark theme.

### Added

- `ActionDialog` "Move to backlog" button — demotes a next action to `BACKLOG` and closes; only shown when opened as a standalone next action (not from `ProjectDialog` or `BacklogPanel`).
- `BacklogLens` — filters all workspace nodes by `status == BACKLOG`, excluding structural area nodes.
- `BacklogPanel` — shows all backlog actions; double-clicking opens `ActionDialog`.
- "Backlog" navigation entry in `MainFrame` between Next Actions and Raw Tree.

### Changed

- `NextActionsLens` now filters all workspace nodes by `status == NEXT` rather than reading structural children of `nextActionsNodeId`. Actions under any project with NEXT status will appear in the Next Actions view.
- `convertInboxItemToNextAction` now sets `status = NEXT` on the node in addition to moving it to the Actions area, so the node immediately appears in the Next Actions view.
- Inbox process dialog option renamed from "Single action" to "Next action".
- `NodeDialog` status toggle now handles `BACKLOG`: BACKLOG/DONE both show "Mark next" and promote to NEXT; NEXT shows "Mark done".
- Actions area node renamed from "Next Actions" to "Actions" — it is now the structural home for all standalone actions regardless of `NEXT`/`BACKLOG` status.
- `NamWorkspaceService.markBacklog(UUID)` added — sets `status = BACKLOG` and saves; symmetric counterpart to `markNext`.

### Changed

- `NodeStatus`: renamed `ACTIVE` → `NEXT`; added `BACKLOG`. Old saved files with `"ACTIVE"` deserialise to `NEXT` via `@JsonAlias`. New nodes default to `BACKLOG` — promotion to `NEXT` is an explicit act.
- `NamWorkspaceService.markActive()` renamed to `markNext()` to match the new status name.
- `NodeDialog` toggle button label updated from "Mark active" to "Mark next".

### Added

- `NamWorkspaceService.convertNextActionToProject()` — moves a next action to the Projects area; refactored shared logic into `convertFromArea()` helper.
- `ActionDialog extends NodeDialog` — adds a "Make project" toolbar button that promotes the action and closes the dialog; `NextActionsPanel` now opens `ActionDialog` on double-click. Button is hidden when opened from `ProjectDialog` (sub-project support is tracked in #39).
- `ProjectDialog extends NodeDialog` — shows a scrollable "Actions" list of the project's direct children; double-clicking a child opens `ActionDialog` for it and refreshes the list on close; `ProjectsPanel` now opens `ProjectDialog` on double-click.
- `NodeDialog.addToolbarButton(JButton)` and `addBelowDescription(JComponent)` — protected hooks for subclasses to extend the toolbar and inject content below the description area.

### Added

- `NodeDialog` toolbar: Delete button with confirmation dialog; non-leaf nodes show an error instead of deleting.
- `NodeDialog` toolbar with a Mark done / Mark active toggle button; status persists immediately.
- `NamWorkspaceService.markActive()` reverses `markDone`, setting status back to ACTIVE.
- Double-clicking a project opens `NodeDialog` for that node; description persists across restarts.
- Double-clicking a next action opens `NodeDialog` for that node; description is saved on confirm and persists across restarts.
- `NodeDialog`: modal dialog showing node title and an editable description text area with Save/Cancel; reusable for actions and projects.
- `description` field on `NamNode` (nullable String, serialised automatically by Jackson).
- `NamWorkspaceService.updateDescription(UUID, String)` — sets description on a node and saves.

### Added

- `NextActionItemRow` record and `NextActionsLens` projection; `NextActionsPanel` renders next actions in a `JTable` (Title/Status columns); done rows in grey; wired to the Next Actions nav entry.
- `ProjectItemRow` record and `ProjectsLens` projection; `ProjectsPanel` renders projects in a `JTable` (Title/Status columns); done rows in grey; wired to the Projects nav entry.
- "Process…" right-click action on inbox items: dialog asks "Single action" or "Project" and converts the item accordingly; disabled for done items.
- `NamWorkspaceService.convertInboxItemToProject()` and `convertInboxItemToNextAction()` move an inbox item to the respective structural area; throw `IllegalArgumentException` if the node is not found or not an inbox child.
- `projectsNodeId` and `nextActionsNodeId` well-known fields in `NamWorkspace`; `createDefault()` creates "Projects" and "Next Actions" child nodes under root alongside "Inbox".
- `JsonWorkspaceRepository` persists all three well-known IDs; old files without `projectsNodeId` or `nextActionsNodeId` are migrated on load.
- `JsonWorkspaceRepositoryTest` covering round-trip persistence and both migration paths.
- `make test` now runs JUnit via `java -cp … ConsoleLauncher` so runtime library JARs (e.g. Jackson) are resolved correctly by the JVM's classpath wildcard expansion.

### Added

- JUnit 5 (`junit-platform-console-standalone-1.10.2`) wired into the project; `make test` compiles and runs the test suite.
- Navigation shell: left nav list (`NavigationEntry`, `NavigationPanel`) and swappable central content area (`ContentArea`) replace the raw split-pane layout.
- Raw tree demoted to a selectable "Raw Tree" navigation view; app opens on "Inbox" by default.
- `inboxNodeId` field in `NamWorkspace`; `createDefault()` creates the Inbox node; `getInboxItems()` and `NamWorkspaceService.addInboxItem()` added.
- `InboxItemRow` record and `InboxLens` projection (`namdesktop.lens` package) — UI renders view models, not raw nodes.
- `InboxPanel` renders inbox items in a `JTable` (Title, Status columns); done rows shown in grey; wired to the Inbox nav entry.
- Inbox commands: Add item (toolbar), Rename, Mark done, Delete via right-click context menu; errors surfaced with dialogs.
- Unit tests for `NamWorkspace` (8 tests covering `createDefault`, `getNode`, and `getChildren`).
- Unit tests for `NamWorkspaceService` (16 tests covering all commands, save behaviour, and error cases; in-memory repository stub — no filesystem I/O).

### Fixed

- `inboxNodeId` was not persisted to `workspace.json`; existing workspace files loaded with `inboxNodeId = null`, silently breaking all inbox mutations. `WorkspaceFile` DTO now includes `inboxNodeId`; old files are migrated on load.

### Added

- Initial project scaffold.
- `NamNode` and `NamWorkspace` domain model with `NodeStatus` enum (`namdesktop.model` package).
- `WorkspaceRepository` interface and `JsonWorkspaceRepository` implementation for JSON persistence (`namdesktop.persist` package).
- `MainFrame` with horizontal split-pane layout (left panel + centre work area).
- `WorkspaceTreeModel` and `TreePanel` display the node tree in the left panel.
- Workspace loaded from `~/.namdesktop/workspace.json` on startup; falls back to a default workspace if the file is absent.
- `NamWorkspaceService` command layer for all workspace mutations (`addChild`, `renameNode`, `deleteLeaf`, `markDone`).
- Right-click context menu on the node tree for adding, renaming, and deleting nodes.
- "Mark done" context menu action; done nodes render with strikethrough and grey text.