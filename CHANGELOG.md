# Changelog

All notable changes to NamDesktop will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

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