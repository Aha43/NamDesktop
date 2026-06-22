# Changelog

All notable changes to NamDesktop will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Fixed

- Workspace loading/cloud-pull now **tolerates unknown JSON fields** (`FAIL_ON_UNKNOWN_PROPERTIES`
  disabled on the workspace mapper). The `workspaces.document` is a shared cross-app contract — NamWeb
  writes the same shape and tends to lead on features — so this keeps a Pull (or an on-disk
  `workspace.json`) from a newer/web-written workspace from throwing on a field this version doesn't
  know yet. Forward-compat for connecting desktop to a hosted NamWeb workspace. Closes #414.
- Cloud sync **Pull** now confirms before overwriting a diverging local workspace. Previously Pull
  saved the remote copy straight over the local file and reloaded, silently discarding any local
  changes made since the last sync (Push was already guarded by the conflict dialog; Pull was not).
  Pull now compares the local and incoming-remote content: if they differ it asks "Pulling will
  replace this workspace with the remote copy. Local changes since your last sync will be lost.
  Continue?" — Cancel keeps the local workspace untouched; when the two already match it applies
  silently. Choosing "Keep remote" in the push conflict dialog still pulls without a second prompt
  (you've already consented to discarding local). Closes #380.
- Heat-map accuracy on both the Goal Board and the Project Workbench readiness view. An empty
  project (no actions) now shows a **neutral gray** border ("no actions yet") instead of red —
  previously a `0 / 0` project read as "no progress." Backed by a new `HeatLevel { NEUTRAL, RED,
  AMBER, GREEN }` on `MissionControlStation` (single source of truth for the thresholds), applied
  to both heat maps. The Workbench readiness view also gains a leading **Unsorted** card for the
  project's own direct actions (omitted when it has none), mirroring the Column view's Unsorted
  column, so loose actions' progress is no longer invisible. Closes #387.
- `workspaces` migration now explicitly grants `select, insert, update, delete` to `authenticated` (and `service_role`). The original table migration relied on Supabase's default privileges to grant DML on new public tables; newer Supabase CLI local stacks no longer do this, so a fresh stack returned "permission denied for table workspaces" (42501) to PostgREST on every NamWeb query. RLS still scopes rows to the owner; the new migration is idempotent. Closes #388.

### Added

- Archive finished projects: right-click a top-level project in the Projects list → **Archive** to drop
  it out of the active list (only live work stays visible); a **Show archived** toolbar toggle reveals
  archived projects (shown muted, marked _(archived)_) where right-click → **Unarchive** restores them.
  Nothing is deleted — archiving sets the project's status to `ARCHIVED` (previously an unused status) and
  `ProjectsLens` filters it out by default. Backed by `archiveProject` / `unarchiveProject` service
  methods. Parity with NamWeb (Aha43/NamWeb#261). Closes #407.
- Bulk actions on selected actions: a checkbox on each action row (with a header select-all on the
  tables) across the list panels — Next Actions, Backlog, Done, Context, Saved Views, Due, Blocked —
  **and the Project Workbench** (ticks shared across a project's own actions and its sub-projects, for
  tidying up a project). A contextual bar appears when ≥1 action is checked, offering **Set status**
  (Next/Backlog/Done), **Add tag**, and **Delete** applied to every checked action in one save. Backed
  by new `setStatusForAll` / `addTagToAll` service primitives (reusing `deleteLeaves`) and a shared
  `CheckColumn` / `BulkActionBar` / `BulkSelect` core. Parity with NamWeb (Aha43/NamWeb#259, #249,
  #250). Closes #402, #411.
- Copy a project summary as Markdown: a copy button in the Project Workbench breadcrumb bar opens a
  summary dialog — a live preview of the project as a Markdown checklist (actions as `- [ ]` / `- [x]`
  with a status suffix, sub-projects as nested headings), an **Include sub-projects** toggle, and a
  **Copy to clipboard** button. Good for pasting a status snapshot into a chat or AI assistant. Backed by
  a pure, testable `ProjectSummary` serializer. Parity with NamWeb (Aha43/NamWeb#245, #247, #257).
  Closes #404.
- Re-triage in Focus mode: the focus deck now has a secondary button to flip a mis-filed card without
  leaving focus — **Move to Backlog** in the Next Actions deck, **Move to Next** in the Backlog deck. Like
  Done, the card changes status, drops out of the deck, and the next one advances. Omitted on the
  status-mixed decks (Project Workbench focus and Saved Filter). Parity with NamWeb#277. Closes #400.
- Theme-adaptive icon color: the SVG icons (pitch-black `currentColor` strokes) are now recolored to
  follow the theme — light gray in dark mode, near-black in light mode — so they're legible against the
  panels instead of nearly invisible black-on-dark-gray. Done via a global `FlatSVGIcon.ColorFilter` in
  the new `UiTheme` look-and-feel helper. Closes #398.
- Settings → Appearance **Background brightness** slider: lightens the panel/surface grays by a chosen
  percentage toward white for a softer dark theme (0 = stock). Persisted in `AppSettings`, applies live.
- Settings → Appearance **Icon color** choice: Auto (follow theme), Light (white icons), or Dark (black
  icons) regardless of theme — pairs with the brightness slider for full control over icon contrast.
  Persisted in `AppSettings`, applies live.
- Inbox multi-select and bulk delete: each row has a leading **checkbox** (with a header checkbox to
  select/clear all); tick the items you want and click **Delete checked** in the toolbar to remove them
  all in one step, after a single "Delete N items?" confirmation. Right-clicking a single row still shows
  the full Process / Rename / Mark done / Delete menu. Backed by a new `deleteLeaves` service primitive
  that removes all selected leaves in one save and reports any skipped (had sub-items). Closes #273, #395.
- Projects view layouts: the top-level **Projects** view now offers the same Column and Readiness
  layouts as a single project's workbench, via a List / Columns / Readiness toggle in its toolbar.
  **List** stays the default compact table; **Columns** lays each top-level project out as a column
  of its actions (drag a card between columns to reparent it); **Readiness** shows a progress
  heat-map card per project. The existing tag filter narrows all three, drilling into a project opens
  its full workbench, and the chosen layout is remembered across restarts. The board layouts reuse
  the workbench renderer in an embedded mode (no duplicate board code). Closes #383.
- Focus mode in the Project Workbench: a deck (stack-2) button in the breadcrumb bar opens the
  one-card-at-a-time focus deck over the current project's own open direct actions (excludes done
  and sub-project actions), so you can work a single project end to end. Reuses the existing
  `MoonCardPanel` — mark-done-and-advance, Esc/Space/←/→. Disabled when the project has no open
  direct actions. Closes #385.
- `workspaces` table is now published to the Supabase Realtime change feed (`alter publication supabase_realtime add table workspaces`), so clients can react live to row updates. Unblocks NamWeb's live-SPA updates (remote-MCP P3): subscribers use a signal-then-pull pattern, and RLS still scopes deliveries to the owning user. Closes #371.
- Column view — collapse columns: each column header has a chevron that collapses it into a narrow titled strip, so the important columns get room. Collapsed columns are remembered per project across restarts. Closes #365.
- Column view — rearrange columns: ◀ ▶ buttons on a column move it left/right; because a column is a sub-project, this reorders the sibling sub-projects in the model (also reflected in the Workbench view). The Unsorted column stays pinned first. Closes #366.
- Column view lanes: a lanes button cycles each column through actions-only (default), actions + sub-projects (two lanes), and sub-projects-only. Actions and sub-projects can both be dragged between columns to reparent them; double-click a sub-project to drill in. Closes #356.
- Project Workbench view switcher: the breadcrumb bar now has an explicit Workbench / Columns / Readiness trio, so there's always a clear way back to the default view.
- Project Workbench remembers each project's view: the view you leave a project in — Workbench, Columns (with whichever lanes and collapsed columns), or the readiness board — is restored when you navigate back, and saved across restarts so the app reopens each project exactly as you left it. Closes #360, #368.
- Column view drag-and-drop: drag a card onto another column to reparent the action there, or drag it within a column to reorder it. The existing move button and right-click menu still work. Backed by a new positional `moveNodeBefore` service primitive (`moveNode` now delegates to it). Closes #355.
- Column view in the Project Workbench: when a project has sub-projects, a new columns toggle in the breadcrumb bar lays each sub-project out as a column, with its direct actions as cards (plus a leading "Unsorted" column for the project's own actions). Move a selected card to another column — via the column's move button or a right-click menu — to reparent the action to that sub-project; no separate board state, just a structural move. Help: new Column view concept article, linked from Project Workbench and Getting Started. Closes #354.
- Local Supabase stack for the cloud-sync PoC (experiment/cloud branch): Supabase CLI config (`supabase/config.toml`), `workspaces` table + RLS policy as a checked-in migration, test-user setup, and `docs/features/supabase-poc/setup.md` covering install, start/stop, env vars, and the later hosted-Supabase move. Closes #348.
- Supabase PoC spike (`namdesktop.spike.SupabaseSpike`, run via `make spike`): signs in to Supabase with email/password, inserts/pulls/updates a workspace JSONB document, and verifies optimistic conflict detection (stale `version=eq.N` PATCH updates 0 rows). Zero new dependencies — `java.net.http.HttpClient` + Jackson. Closes #349.
- Cloud sync settings: new "Cloud sync (Supabase)" group in Settings → Sync — enable toggle, Supabase URL and publishable key (defaulting to the local stack for zero-config dev), email, and masked password with show/hide. Persisted as `cloudSync` in settings.json via the new `CloudSyncSettings` model; no sync logic yet. Closes #215.
- `CloudSyncService` interface + `SupabaseSyncService` implementation: push (version-guarded PATCH, first-push insert, vanished-row recovery), pull (JSONB document → workspace), and conflict detection via returned-row count. Fresh sign-in per operation, no token refresh. `JsonWorkspaceRepository` gains `toJson`/`fromJson` string converters reused by the service. UI entry points come in #217. Closes #216.
- Cloud sync UI: toolbar Push/Pull and File menu now dispatch to Supabase when cloud sync is enabled (Git sync otherwise; cloud wins when both configured), reacting live to the Settings toggle. Push shows "Synced — [time] (vN)" in the status bar; pull applies the remote workspace in place without a restart. Conflicted push opens a Workspace conflict dialog: Keep remote / Keep local / Cancel. Help: Settings article updated, new Cloud sync concept article in the sidebar and Getting Started. Closes #217.
- Workspace-name-aware sync engine: `SupabaseSyncService` can target a named remote workspace row (`default` or `dev`), each with its own version watermark in `CloudSyncSettings` — a dev sync never moves the default workspace's watermark. New migration adds a unique index on `(owner_user_id, name)`. Groundwork for dev-mode cloud sync (UI in #351). Closes #350.
- Dev-mode cloud sync: cloud Push/Pull now works in dev mode too, targeting the separate `dev` remote workspace — the real workspace and the dev sandbox sync independently and can never mix. Tooltips and status messages name the target ("Supabase (dev)", "Synced — [time] (dev, vN)"). Git sync remains unavailable in dev mode. Help: Cloud sync article gains a Dev mode section. Closes #351.

### Changed

- An action's **project path is now a clickable breadcrumb**: each segment is a link that opens that
  ancestor project. Works everywhere an action lists its path — Next Actions, Backlog, Context, Due,
  Blocked, Done, saved views, and the focus deck (`MoonCardPanel`). In the tables the path column is
  link-styled with a hand cursor; clicking a segment resolves which project was hit and navigates to
  it. Parity with NamWeb (Aha43/NamWeb#167). Closes #382.
- Newly added actions and sub-projects now **land first** in their parent's list instead of last,
  so a freshly captured item is immediately visible at the top of long lists. Applies to every
  single-node add path — the Workbench/Inbox/Next-Action add buttons and the MCP `add_*` tools —
  matching NamWeb's prepend-on-add behavior (Aha43/NamWeb#174). Moves, conversions, and template
  application keep their existing ordering. Closes #386.
- Help sidebar cross-lists articles that belong in more than one section: Goal Board now also appears under Projects; Due Actions and Blocked actions also under Finding work; Focus mode also under Daily workflow. Closes #363.
- Settings dialog restructured: left sidebar (Appearance / Workspace / Sync) replaces the flat single-column layout. Dialog is now resizable. Closes #344.

### Added

- New setting: **Click to rename** (default on). When off, clicking an already-selected row title no longer triggers inline rename. All other rename paths (Rename button, right-click, double-click, Enter) still work. Affects Next Actions, Backlog, Context, Saved Views, Done, Projects, and Project Workbench. Closes #343.

### Changed

- Splash dialog no longer has a Settings tab. Launch prompt now shows only the dev-mode checkbox and Launch button. Settings remain fully accessible from inside the app. Closes #342.

### Added

- Back navigation button (toolbar) and `Cmd+[` keyboard shortcut. Keeps a capped history of up to 20 locations (panel views, project workbenches, saved views). Silently skips deleted projects when popping. Closes #337.
- Re-clicking an already-selected nav item now always navigates to that section's root view. Previously the `ListSelectionListener` fired no event on re-click, leaving the content area unchanged. Closes #336.

### Added

- Monitoring mode guard: all mutating UI actions (status change, save, delete, rename, reorder, add item/action) prompt before executing when monitoring mode is active. Dialog offers Exit / Continue (don't ask again this session) / Cancel. One "Continue" suppresses further prompts for the rest of the session. Closes #339.

### Added

- `NamMcpServer --direct` mode: pass `--direct` alongside `--workspace` to bypass monitoring mode and write straight to `workspace.json`. Writes a `.namdesktop-direct` PID sentinel on startup; deletes it on clean exit via shutdown hook. `get_monitoring_status` reports direct mode. Swing app warns on startup if a live direct-mode server is detected; silently cleans up stale sentinels. Closes #334.

### Added

- Due Actions panel: "Due" nav entry (after Blocked, before Done) showing all non-done actions with a due date grouped into Overdue (red), Today (amber), This week (muted blue), and Later sections. Empty sections hidden. Rows support status badge, inline rename, project path, tags, and due column. Closes #331.
- Due hints column in Next Actions, Backlog, Context, and SavedView panels: narrow "Due" column showing overdue (`2d ago`, red), today (`Today`, amber), this week (day name, muted blue), or later (short date, default). Blank when no due date set. Closes #330.
- Due date field in `ActionDialog`: "Due:" row with ISO date entry (`YYYY-MM-DD`), placeholder text when unset, Clear button, inline error on invalid input. Saves via `setDueDate` on the service. Closes #329.
- `dueAt` (`LocalDate`) field on `NamNode` — optional deadline pressure on actions. Serialised as ISO date string; absent from JSON when null; existing nodes load with null (no migration needed). `NamWorkspaceService.setDueDate()` sets or clears the field and touches `updatedAt`. Closes #328.

### Added

- Age indicator column on Inbox, Next Actions, and Backlog rows: narrow "Age" column between title and tags showing relative age (`3d`, `2w`, `4m`, `1y`) based on `updatedAt` falling back to `createdAt`; blank when both are null. Inbox items older than 7 days shown in amber; all other panels use muted text. Closes #326.
- FIFO/LIFO sort toggle on Inbox, Next Actions, and Backlog toolbars: clock-up/clock-down button cycles through no sort → oldest first (FIFO) → newest first (LIFO). Null-timestamp items sort last in both directions. Sort persisted per panel in settings. Up/down ordering buttons hidden when sort is active. Closes #327.

### Added

- Help: sidebar reorganised into named sections (Tutorial, Daily workflow, Projects, Finding work, App, Superpower, Background) with visual separators and logical reading order. AI assistant spotlighted in its own section and given dedicated framing in Getting Started. Closes #324.
- Help: Search, Tag management, Keyboard shortcuts, and Settings reference articles — four short articles covering features that had no documentation. All added to HelpPanel sidebar; Keyboard shortcuts linked from Getting Started. Closes #323.
- Help: Blocked actions concept article — explains blocking relationships, the Blocked panel, how to add/remove blockers in the action dialog, and cycle detection. Added to HelpPanel sidebar; cross-linked from Next Actions and Getting Started. Closes #322.
- Help: Resources concept article — explains the four attachment types (URI, File, Email, Text), how to add and remove resources via the action/project dialog, and the paperclip indicator in list views. Added to HelpPanel sidebar; cross-linked from Projects, Project Workbench, and Getting Started. Closes #321.
- Help: Saved Views concept article — explains what Saved Views are, how to create them, the next-only toggle, and how they differ from the session-only Tag filter panel. Added to HelpPanel sidebar; cross-linked from the Tag filter article. Closes #320.
- Help: Templates concept article — explains what templates are, how to create one from an existing project, how to apply a template, and when they are useful. Added to the HelpPanel sidebar; cross-linked from Projects and Project Workbench articles. Closes #319.

### Changed

- Help: GTD tone audit — all articles now lead with plain language; GTD references demoted to brief italic asides where they appear. Getting Started no longer frames GTD as a prerequisite. Projects article opens with a plain definition. GTD article reframed as "the thinking behind NAM" for the curious. Closes #318.

### Added

- Help: pop-out button (top-right of help panel) opens help in a non-modal floating dialog so the user can read and use the app simultaneously. F1 / Help menu raises the floating dialog when already open. Dialog position and size are persisted in settings. Closes #317.

- Project Workbench power mode: Rename, Edit description, and Delete buttons for the current project moved from the "This project" section header to the breadcrumb toolbar (upper right, next to the pencil). Sub-project section headers are unchanged. Closes #316.

- Move to…: actions can now be moved to the free (standalone) actions area via a "(Free action)" entry at the top of the picker — analogous to moving a project to top-level. MCP `move_node` also accepts `nextActionsNodeId` as target, and omitting `new_parent_id` for an action now moves it to free actions instead of returning an error. Closes #314.

### Fixed

- Monitoring mode: `move_node` MCP changes are no longer silently dropped at checkpoint. `MonitoringMode.diff()` now detects when an existing node's parent differs between baseline and external and counts it as a `moved` change; `DiffSummary.describe()` surfaces "N node(s) moved" in the checkpoint dialog. Closes #313.

### Added

- MCP: `list_backlog` tool — lists all nodes with status BACKLOG across the workspace, consistent with `list_next_actions` and `list_done`. Closes #311.
- MCP: `add_blocked_by` / `remove_blocked_by` tools — add or remove a blocking relationship on an existing node after creation. Cycle detection prevents circular dependencies. Requires monitoring mode. Closes #310.
- MCP + UI: `move_node` MCP tool reparents a node within the project forest; actions may be moved between projects, projects may be moved to any project or top-level. Moving an action into a non-project is rejected (use "Make project" first). "Move to…" button added to ActionDialog (moves the action to a different project) and ProjectDialog (moves the project to a different parent or top-level). Closes #309.
- `Cmd+S` / `Ctrl+S` keyboard shortcut in the File menu: pushes the workspace to the configured git sync remote when sync is set up; otherwise shows a brief "Workspace auto-saved locally." nudge confirming that auto-save is active. Closes #241.
- Power user mode: Settings toggle (off by default) that restores the full inline toolbar in Project Workbench — section headers gain Rename, Description, and Delete buttons; action toolbars gain Rename and Edit tags; breadcrumb ancestor links gain a pencil. All removed by #227; now opt-in. Closes #235.
- Project Workbench: "Make project" button in ActionDialog converts an action to a sub-project in place — the action gains `isProject=true`, disappears from the actions list, and becomes a new sub-project section on the next rebuild. Closes #39.

### Fixed

- MCP test plan: `docs/test/mcp-testplan.md` — one section per tool, Setup/Action/Assert/Cleanup pattern, Python3 assertions against `workspace.json`, cleanup pass. Closes #306.
- MCP server: `update_node` tool — update title, description, and/or tags on an existing node in place. All fields optional; omit to keep existing, empty string clears description, empty array clears tags. Requires monitoring mode. Closes #303. Supersedes #301 (set_tags).
- Monitoring mode: resource edits (and any MCP change followed by an in-app save) now correctly trigger the accept/reject prompt on exit/checkpoint. Root cause: `onExternalChange` reloaded the workspace into memory; any subsequent in-app save (e.g. opening a dialog triggering `touchNode`) wrote MCP changes to `workspace.json`, making both files identical and silencing the diff. Fix: `enter()` now saves a `workspace.monitoring-base.json` snapshot; `exit()` diffs against this baseline instead of the (potentially corrupted) `workspace.json`. Closes #307.
- MCP server: `edit_resource` tool — update value and/or description of an existing resource in place by node id and zero-based index. Both fields optional; omit to keep existing, pass empty string to clear description. Requires monitoring mode. Closes #300.
- Monitoring mode: persistent amber status bar at the bottom warns "in-app edits are not captured by checkpoint" while monitoring is active. Checkpoint dialog also shows this note. Closes #304.
- Monitoring mode: checkpoint confirm dialog no longer re-appears on exit when no new changes exist. Root cause: `Resource` lacked `equals()`/`hashCode()`, so the post-checkpoint diff always reported resource changes even when both files were identical. Closes #302.

### Added

- Help: Done concept article added; Done entry added to the left-hand concept index. Fixes broken `concept://done` link in the Next Actions article. Closes #299.

- Backlog panel: top-level project filter strip — visible in "all actions" mode; one chip per top-level project with ≥1 backlog action; click to narrow the list, multiple chips = OR; session-only state. Closes #290.
- Backlog panel: free-actions toggle — default shows only free (standalone) actions; toggle to reveal all backlog actions including project actions; persisted in settings. Closes #289.

- E2E coverage for resources: `addResource`, `assertHasResource`, and `assertResourceCount` wired into `NamDemoWiring` / `NamAssertWiring`; resource scenario added to `e2e.json`. Closes #287.

- `Cmd+N` / `Ctrl+N` keyboard shortcut for context-sensitive new item: creates an inbox item when Inbox is active, a new project when Projects is active, and a new action in the current project when the Project Workbench is active; no-op on other panels. Shortcut is listed in the Keyboard Shortcuts dialog. Closes #244.

- Branding: NamDesktop logo wired into the app. `logo-tile.svg` appears in the Splash dialog above the app name; `logo-wordmark.svg` replaces the plain text name in the About dialog; `logo-mark.svg` is rendered at 16/32/64/128 px and passed to `JFrame.setIconImages` for the window icon and alt-tab. Closes #246, #248.
- macOS packaging: `package-macos.ps1` now passes `--icon NamDesktop.icns` to jpackage when the file exists; `scripts/make-icns.sh` generates the `.icns` from `assets/logo-mark.svg` using `rsvg-convert` and `iconutil`. Closes #247.

- "Seen" touch: opening an ActionDialog or ProjectDialog sets `updatedAt` on the node so staleness reflects actual review activity, not just edits. Closes #284.

- `NamNode` timestamps: `createdAt`, `updatedAt`, `statusChangedAt` (all `LocalDateTime`, nullable). Set on creation and wired through all mutations and status transitions in `NamWorkspaceService`. Serialised as ISO-8601 strings via Jackson (`jackson-datatype-jsr310`). Existing JSON files without these fields deserialise cleanly with `null`. Closes #283.

### Fixed

- `add_resource` MCP tool now persists correctly: `MonitoringMode.diff()` now counts resource additions and modifications on existing nodes as a change, so the Checkpoint / Accept dialog triggers instead of silently discarding the external file. Closes #282.

- Resources section in ActionDialog and ProjectDialog now starts expanded so the add form is immediately visible and Enter in the value field adds a resource instead of closing the dialog. The panel behind the dialog also refreshes in real time when a resource is added or removed.

### Added

- Resource persistence round-trip test (`JsonWorkspaceRepositoryTest`).

- MCP server: three resource tools — `list_resources`, `add_resource`, `remove_resource` — so Claude can read and manage attached resources via the MCP stdio server. Closes #279.

- Paperclip indicator in all list views (Inbox, Next Actions, Backlog, Done, Context, Projects): a small paperclip icon appears in a fixed-width column when a node has attached resources. Closes #278.

- ProjectDialog: collapsible Resources section — same behaviour as ActionDialog; shared implementation lives in NodeDialog. Closes #277.

- ActionDialog: collapsible Resources section — collapsed by default when empty, auto-expanded when populated. Add-form with type selector (URI/EMAIL/FILE/TEXT), value, and optional note. Clicking a resource opens it (browser/mailto/file-manager/clipboard). Resources removed immediately with ✕. Closes #276.

- Resources domain model: `ResourceType` enum (`TEXT | EMAIL | URI | FILE`), `Resource` class (`type`, `value`, `description`), `List<Resource>` on `NamNode` persisted via Jackson; `NamWorkspaceService.addResource` and `removeResource`. Closes #275.

- Bulk action creation: the add-action dialog in Next Actions and Project Workbench now accepts multiple lines — each non-blank line creates a separate action. Single-line entry and "Create & Edit" work exactly as before. Closes #274.

- Keyboard shortcuts reference dialog: `Help › Keyboard Shortcuts…` (`Cmd+/`) opens a sectioned two-column dialog listing all shortcuts with badge-styled keys. Works on macOS (`⌘⇧`) and Windows (`Ctrl+Shift+`). Closes #272.

- Keyboard shortcuts: `Cmd+1…5` jump directly to Inbox, Next Actions, Backlog, Projects, and Done from anywhere in the app. Wired as View menu accelerators. Closes #245.

- Keyboard shortcut: `Cmd+F` opens the Search panel and focuses the search field. Closes #242.

- Dev mode badge: an amber `● Dev` label appears in the toolbar (left of Settings) whenever NamDesktop is launched in dev mode — mirrors the `● Monitoring` indicator style so mode awareness is always visible in full screen. Closes #270.

- Checkpoint: while in monitoring mode, a Checkpoint button (✔) appears in the toolbar and `File › Checkpoint` (Cmd+Shift+S) is enabled. Checkpoint shows the same accept/reject summary dialog, then flushes accepted changes into the main workspace and resets the external baseline — Claude can keep writing without another toggle. Rejecting discards the external changes but stays in monitoring. Closes #269.

- Monitoring mode toolbar indicator: an amber `● Monitoring` label appears in the toolbar when monitoring mode is active — visible even in macOS full-screen mode where the title bar is hidden. Closes #268.

- Accept/reject dialog on app exit during monitoring mode: closing NamDesktop via the window button, Exit menu item, or Cmd+Q while monitoring mode is active now triggers the same accept/reject summary flow instead of silently discarding changes. Closes #264.

- MCP server: unit tests for all tools in `NamMcpServerTest` — read tools, write tools, monitoring mode guard, and error paths. Closes #260.

- MCP server: read tools now read from `workspace.external.json` when monitoring mode is active, so agents can verify their writes in the same session without restarting monitoring mode.

- MCP server: `list_project_children(project_id)` read tool returns the direct children of a project node (id, title, status, project flag, tags). Use before writing to verify the current structure and avoid acting on stale IDs after monitoring mode restarts. Closes #267.

- MCP server: `create_project(title, description?, tags?, parent_id?)` write tool creates a project node. Omitting `parent_id` creates a top-level project; providing it nests the project under an existing node. Returns the new node's id for chaining. Requires monitoring mode. Closes #265.

- MCP server: `add_action(title, project_id, description?, tags?, status?, blocked_by?)` write tool adds an action as a child of a specific project. Status defaults to BACKLOG. Returns the new node's id. Requires monitoring mode. Closes #266.

- MCP server: `delete_node(node_id)` write tool removes a node from the workspace. Rejects nodes that have children (use the app for recursive deletes). Requires monitoring mode. Closes #263.

- MCP server: `list_done` read tool returns all actions with `status: DONE`. Closes #262.

- MCP server: `find_node(title)` read tool finds nodes by case-insensitive substring match against all node titles. Returns id, title, status, project flag, and tags for each match. Enables agents to look up node IDs by name without reading the workspace file directly. Closes #261.

- MCP server: `list_saved_views` read tool returns the workspace's saved views (name, tags, nextOnly). Agents can use these to apply the user's own context filters when listing next actions. Closes #259.

- MCP server: `blockedBy` support in read and write tools. `list_inbox` and `list_next_actions` now include a `blocked_by` array on each item. `add_inbox_item` and `add_next_action` accept an optional `blocked_by` array of node UUIDs; unknown UUIDs are silently ignored. Closes #258.

- MCP server: `list_next_actions` read tool returns all nodes with `status: NEXT` across the workspace, including id, title, status, tags, description, and blocked_by. Closes #257.

- MCP server: `add_next_action(title, description?, tags?)` write tool creates a new action with `status: NEXT` as a child of the Next Actions container in one call, without requiring the caller to read the workspace file. Requires monitoring mode. Closes #256.

- MCP server (`namdesktop.mcp.NamMcpServer`): run from the same JAR as a separate process to expose workspace tools to AI agents via the MCP stdio protocol. Read tools (`get_workspace_context`, `list_inbox`, `list_projects`, `get_monitoring_status`) work without monitoring mode. Write tools (`add_inbox_item`, `mark_next`, `mark_done`, `mark_backlog`) require monitoring mode to be active and use atomic writes via temp-file rename. Returns a plain-text warning if a write is attempted without monitoring mode. Closes #251.

- Monitoring mode live panel refresh: when an external agent writes to `workspace.external.json`, panels now reload immediately so changes (inbox items, projects, status updates) appear live without exiting monitoring mode. Rejecting on exit restores the original workspace. Closes #254.

- Monitoring mode live change detection fix: replaced `WatchService` with a `Files.getLastModifiedTime()` poll every 500ms, resolving missed events caused by atomic renames on macOS. Closes #253.

- Monitoring mode live reactions: while monitoring mode is active, file changes to `workspace.external.json` are detected via `WatchService` and trigger toast notifications. New inbox items also auto-navigate to the Inbox panel. Closes #250.

- Monitoring mode: an antenna toolbar button and Cmd+Shift+M toggle puts the app into monitoring mode, copying the workspace to `workspace.external.json` for external agents to write into. On exit a summary dialog shows detected changes (inbox items added, projects created, status changes, deletions) with Accept and Reject buttons. Accepting replaces the live workspace; rejecting discards the external file. Closes #249.

- First-run welcome screen: new users see a `WelcomePanel` with two choices — "Explore demo workspace" (loads sample data) or "Start fresh" (opens Inbox). The screen is shown once and dismissed by either choice; state is persisted in `settings.json`. Closes #232.

- Demo accessible outside dev mode: "Run Demo…" is now always available in the File menu. If the workspace already contains data a confirmation warning is shown before overwriting. Closes #226.

- Global inbox capture shortcut: Cmd+I (Ctrl+I on Windows/Linux) opens the capture dialog from anywhere in the app via a menu item and a toolbar button. The shortcut is shown in the + button tooltip in the Inbox panel. Closes #238.

- Inbox bulk capture: the Add dialog now uses a multiline text area — each non-empty line becomes a separate inbox item on confirm. Ctrl+Enter confirms; Enter adds a new line. Single-line behaviour unchanged. Closes #237.

- Projects panel tag filter: a tag filter strip appears above the project list when the workspace has tags. Selecting tags narrows the list to matching projects (OR logic); clearing restores all. Session-only state. Closes #236.

- Project Workbench affordance reduction: section headers now show only the pencil (full edit) button; action toolbars show only Add, Edit, and move up/down. Rename, description, tags, and delete shortcuts removed from inline toolbars — all accessible via the edit dialog. Delete in ProjectDialog now uses recursive delete with a count-aware confirmation. Closes #227.

- Goal Board: renamed "Mission Control" to "Goal Board" throughout the UI, nav section, toolbar, menus, and help content. Creation dialog redesigned with tags-first flow and live project preview. Closes #230.

- Inbox processing flow: replaced the flat "Next action / Project" option dialog with a two-step `ProcessInboxDialog` — step 1 chooses action vs. project, step 2 (action path) chooses "Do it next" or "Park for later" (Backlog). Each path shows a status bar nudge after completion. Closes #228.

- Blocked lens: a "Blocked" nav entry (between Backlog and Done) shows all actions with unmet prerequisites, grouped by blocker. Bold header rows open the blocker's dialog; action rows have the full status badge, pencil, and project path. Closes #210.
- Completion nudge: marking an action Done shows a brief "Unblocked: …" message in the status bar listing actions that are now actionable. Fires from the inline status badge popup (Next Actions, Backlog) and the ActionDialog Done button. Auto-hides after 4 seconds. Closes #209.
- Next Actions and Backlog: blocked actions are hidden by default. A padlock toggle button (lock icon) in each panel's toolbar reveals them; when visible, blocked rows are grayed out and hovering the action column shows a "Blocked by: …" tooltip listing the unmet prerequisites. Closes #208.
- Action prerequisites UI in ActionDialog: a "Blocked by" section with an autocomplete search field lets the user link prerequisite actions (cycle-causing candidates are excluded from results). Each linked prerequisite shows a × button for removal. A read-only "Would unblock" section lists actions blocked by this one, each as a clickable link that navigates to that action's dialog. Closes #207.
- Action prerequisites (blocked-by): `NamNode` gains a `blockedBy: List<UUID>` field. New service methods: `addPrerequisite` (with cycle detection), `removePrerequisite`, `isBlocked`, `unblocks`, `canAddPrerequisite`. Deleting a node auto-sweeps its ID from all `blockedBy` lists. Closes #206.

- Inline status badge (N / B / D colored letter) in the Action column of all action panels (Next Actions, Backlog, Done, Context, Saved Views, Project Workbench). Clicking the badge opens a Next / Backlog / Done popup for instant status changes without opening the full dialog.
- Inline rename across all action panels and the Projects panel: clicking an already-selected title overlays a text field for editing in place. Enter or focus-lost commits; Escape cancels.
- Enter key opens the edit dialog on the selected row everywhere a double-click does: Next Actions, Backlog, Done, Context, Saved Views, Project Workbench action list, and Projects panel.
- Explicit pencil (Edit action…) button in the Project Workbench action bar — opens the full ActionDialog for the selected action, consistent with the pencil button on project section headers.

### Added

- In-app help browser: a "Help" entry in the nav sidebar opens a three-pane browser with a tutorial list, a tutorial content pane (HTML), and a concept article pane that slides in when a concept link is clicked. Initial content: "Getting started" and "Planning a goal with MCR" tutorials, plus eight concept articles (GTD, Inbox, Projects, Next Actions, Contexts, Backlog, Mission Control, Project Workbench). Concept links use a `concept://slug` URL scheme intercepted in-process. Closes #202.

- MCR view mode for any project node: a dashboard toggle button in the Project Workbench opens sub-projects as heat-map station cards. Clicking a card navigates into that sub-project; navigating back restores MCR mode automatically. Closes #200.

- Full project path shown in the Project column of all action list views (Next Actions, Backlog, Done, Context, Saved Views). Project column fills all available horizontal space; tooltip retained as fallback. Action column widened for readability.

### Fixed

- Inbox view: removed always-redundant Status column (items are always unprocessed).
- Project Workbench: "This project" section header now aligns with sub-project section names.

### Added

- Mission Control: tagged-project dashboard accessible from the nav panel and a dedicated toolbar button. Each Mission Control groups projects by one or more tags into a heat-map grid of station cards (red / amber / green border by done ratio). Clicking a card opens the Project Workbench with the MC name in the breadcrumb. Supports create, delete, multi-tag OR matching, deduplication of nested tagged projects, and rolled-up sub-project stats. Closes #197.

- Done lens view: confirm dialog before "Mark as Next" / "Mark as Backlog" to prevent accidental resurrection; first row is auto-selected when entering the Done view. Closes #193.

- Moon Cards: consistent all-done state — counter shows "0 / 0" and card title shows "All done!" when the last card is marked done. Closes #194.

- Moon Cards: Esc key exits deck mode; exit button label updated to "Exit  [Esc]" for consistency with other shortcut hints. Closes #196.

- "Saved Views" section header in the navigation panel: replaces the bare separator with a small dimmed label and top border, making the split between built-in lenses and user-created saved views immediately clear.

- Done lens view: selection is preserved after delete or resurrect — the next row is automatically selected so you can keep cleaning without re-clicking.

- Done lens view: a dedicated "Done" nav entry (below Backlog) lists all completed actions in grey. Toolbar provides Delete (with confirmation), Mark as Next, and Mark as Backlog for housekeeping and resurrection. Closes #192.

- Moon Cards deck mode for Next Actions, Backlog, and Saved Views: a stack-2 icon button in each lens toolbar opens a full-screen card view showing one action at a time (title, description, project path). Navigate circularly with Previous/Next, mark an action done and advance with Done, or exit back to the list with the exit button. Uses CardLayout internally for reliable exit in all window modes including macOS native full screen. Closes #190.

- Inherited project tags visible in all action list views (Next Actions, Backlog, Context, Saved Views): tags inherited at query time from ancestor projects appear in italic after the action's own tags, making it clear why an action matched a filter. Closes #178.

- Rename saved view: cursor-text icon button in the saved view header renames the view in place; the navigation panel updates immediately with the new name selected. Closes #177.

- "Run Demo…" restricted to dev mode only and resets the workspace to a clean default state before running — gives a reproducible known-good dataset for manual testing with one click. Closes #175.

- Recursive delete for non-empty projects: deleting a project that contains actions or sub-projects now works from both the workbench delete button and the raw tree context menu. A confirmation dialog shows the exact blast radius ("This will also permanently remove N sub-project(s) and M action(s).") before any data is removed. Next Actions and Backlog views update immediately. Closes #174.

- `make e2e` target: launches the app with `--e2e` flag against a fresh empty workspace, runs `e2e.json` (14 build steps + 16 assertions), and exits 0/1 — ready for regression runs without a full framework. `NamAssertWiring` provides the six assertion handlers; any unhandled exception or failed assertion increments the failure count and results in a non-zero exit. Closes #172.

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