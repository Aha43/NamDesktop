# Changelog

All notable changes to NamDesktop will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

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