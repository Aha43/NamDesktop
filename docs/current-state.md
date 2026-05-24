# NamDesktop — Current Implementation State

> This document describes the current implementation state, not the final architecture.
> Update it at the end of each sprint so design discussions stay grounded.

Last updated: 2026-05-24 (after sprints through #179 — dogfooding polish, e2e, recursive delete, tag inheritance, workbench, templates, sync)

---

## Stack

Java 21 · Swing · No Maven/Gradle (plain Makefile) · FlatDarkLaf / FlatLightLaf · Jackson · JSVG · JUnit 5
Source: `src/namdesktop/` · Deps: `lib/` · Tests: `test/namdesktop/`
Icons: Tabler Icons SVG (`src/icons/`), loaded via `FlatSVGIcon`, `currentColor` adapts to theme.

Build targets: `make` (build) · `make run` · `make test` · `make e2e` · `make clean` · `make help`

---

## Layers

```
NamNode / NamWorkspace          domain model
    ↓
NamWorkspaceService             command layer (all mutations, single save per command)
    ↓
Lens projections                stateless views over workspace
    ↓
Swing panels / dialogs          UI
    ↓
JsonWorkspaceRepository         JSON persistence (~/.namdesktop/workspace.json)
```

---

## Domain (`namdesktop.model`)

**NamNode** — `UUID id`, `String title`, `String description`, `NodeStatus status`, `boolean isProject`, `List<UUID> childIds`, `List<String> tags`

**NodeStatus** — `NEXT | BACKLOG | DONE | CANCELLED | ARCHIVED`
- `@JsonAlias("ACTIVE")` on `NEXT` for backward compatibility
- New nodes default to `BACKLOG`; promotion to `NEXT` is explicit

**Tags** — plain strings, normalised to lowercase. Convention: `@context` for GTD contexts. Not enforced by model.

**NamWorkspace** — `LinkedHashMap<UUID, NamNode> nodes` (insertion-order preserved)
Well-known UUIDs: `rootNodeId`, `inboxNodeId`, `projectsNodeId`, `nextActionsNodeId`
Tag registry: `List<String> registeredTags`
Saved views: `List<SavedView>`
Templates: `List<ProjectTemplate>`
View orders: `Map<String, List<UUID>>`

Key methods:
- `getNode(UUID)`, `getChildren(UUID)`, `getParent(UUID)`, `getInboxItems()`
- `buildPath(UUID) → List<NamNode>` — ordered path from root to node
- `collectSubtree(UUID) → List<UUID>` — BFS walk of all descendant IDs including root
- `effectiveTags(UUID) → Set<String>` — node's own tags unioned with tags from `isProject==true` ancestors (query-time inheritance)
- `allTags() → List<String>` — sorted deduplicated union of registry + node tags
- `resetToDefault()` — resets workspace in-place to default state (used by dev demo)
- `createDefault()` — factory: root("NAM") → [Inbox, Projects, Actions]

**SavedView** — `record(String name, List<String> tags, boolean nextOnly)`

**ProjectTemplate / TemplateNode** — recursive template structure for creating project hierarchies from a named template.

---

## Service (`namdesktop.service`)

`NamWorkspaceService` owns all mutations:

| Method | Effect |
|---|---|
| `addChild` | Adds child node (sets `isProject=true` if parent is projectsNode) |
| `addSubProject` | Adds child with `isProject=true` |
| `renameNode` | Renames |
| `deleteLeaf` | Deletes leaf only (throws if has children) |
| `deleteRecursive` | Deletes node and entire subtree; scrubs view orders |
| `resetWorkspaceToDefault` | Resets workspace in-place and saves (dev demo) |
| `markDone / markNext / markBacklog` | Status transitions |
| `updateDescription` | Persists description |
| `addInboxItem` | Adds to inbox |
| `createNextAction` | Creates action in next-actions area with NEXT status |
| `createBacklogAction` | Creates action in next-actions area with BACKLOG status |
| `convertInboxItemToNextAction` | Moves to Actions area + sets NEXT |
| `convertInboxItemToProject` | Moves to Projects area |
| `convertNextActionToProject` | If parent is nextActions area → moves to Projects; else sets `isProject=true` in place |
| `convertProjectToAction` | Demotes leaf project; top-level → Actions area + NEXT; sub-project → stays + NEXT; throws if has children |
| `registerTag / addTag / removeTag / updateTags` | Tag node operations |
| `renameTag` | Renames tag across all nodes |
| `deleteTag` | Removes tag from registry and all nodes |
| `createSavedView(name, tags, nextOnly)` | Adds named saved view |
| `deleteSavedView` | Removes saved view by name |
| `renameSavedView` | Renames saved view preserving tags and nextOnly |
| `getViewOrder / moveViewItemUp / moveViewItemDown` | Persistent manual ordering for next-actions and backlog views |
| `createTemplateFromProject` | Captures project subtree as a named template |
| `applyTemplate` | Instantiates a template under a target project |
| `deleteTemplate` | Removes named template |

---

## Lens projections (`namdesktop.lens`)

Stateless, return immutable view-model records. Structural nodes always excluded.

| Lens | Record fields | Filter |
|---|---|---|
| `InboxLens` | `id, title, status` | children of inboxNodeId |
| `ProjectsLens` | `id, title, tags` | children of projectsNodeId |
| `NextActionsLens` | `id, title, status, parentTitle, parentId, isSubProject, projectPath, tags, inheritedTags` | `status == NEXT && !isProject` |
| `BacklogLens` | `id, title, status, parentTitle, parentId, isSubProject, projectPath, tags, inheritedTags, isInboxItem` | `status == BACKLOG && !isProject` |
| `ContextLens` | `id, title, status, parentTitle, tags, inheritedTags` | `!DONE && !isProject` + all required tags present (AND, via `effectiveTags`); optional `nextOnly` |
| `ProjectWorkbenchLens` | `WorkbenchProjection` — current project, child projects (with actions), breadcrumb path | scoped to one project |

`parentTitle/parentId` — non-null when node is a child of a non-structural parent.
`isSubProject` — true when parent's parent is not `projectsNodeId`.
`projectPath` — human-readable ancestor path, e.g. `"Home > Kitchen"` (tooltip).
`inheritedTags` — tags from `effectiveTags()` minus the node's own tags (shown italic in UI).

**Tag inheritance**: `effectiveTags(nodeId)` walks ancestors at query time, unioning tags from `isProject==true` nodes. Removing a project tag immediately affects all descendants.

---

## UI (`namdesktop.ui`)

**MainFrame** — left `NavigationPanel` + swappable `ContentArea` + toolbar + `JMenuBar`

Launch modes:
- Normal: `SplashDialog` prompts dev/prod selection → loads workspace from disk
- Dev (`[DEV]` title): loads from `~/.namdesktop/dev/workspace.json`
- E2E (`--e2e` flag): fresh in-memory workspace, runs `e2e.json`, exits 0/1

Nav entries: Inbox · Projects · Next Actions · Context · Backlog · [Raw Tree — dev only] · [saved view entries]

**Panels** (each has `refresh()`):

| Panel | Notable behaviour |
|---|---|
| `InboxPanel` | Title, Status; right-click: add/rename/mark done/delete/process |
| `ProjectsPanel` | Title, Tags (no Status column) |
| `NextActionsPanel` | Title, Project (italic if sub-project, tooltip path, single-click navigates to workbench), Tags (inherited italic), Status; manual up/down ordering |
| `BacklogPanel` | Same as NextActionsPanel; inbox items rendered italic |
| `ContextPanel` | Tag checkbox selector (AND, match count, Clear); table: Title, Project, Tags (inherited italic) |
| `SavedViewPanel` | Name + filter summary header; rename (cursor-text icon) + delete (trash icon) buttons; table: Title, Project, Tags (inherited italic); Add action button |
| `ProjectWorkbenchPanel` | Breadcrumb nav; sub-project sections with action lists; quick rename/description/edit/delete buttons per sub-project; delete is recursive with blast-radius warning |
| `TreePanel` | Raw node tree; context menu: add/rename/mark done/move/delete (recursive with count warning) — dev only |

**Dialogs** (all `APPLICATION_MODAL`):

`NodeDialog` (base) — title, radio group status control (Backlog / Next / Done), description textarea, `TagsField` (autocomplete), Save/Cancel

`ActionDialog extends NodeDialog` — "Make project" button; project context row with "Open project" link when inside a project

`ProjectDialog extends NodeDialog` — child list table (Title, Tags, Status); "Convert to action" + "Add action" toolbar buttons

`TagManagementDialog` — Tag, Used-by table; New/Rename/Delete toolbar; fires `onChanged` callback immediately on rename or delete so active panel refreshes live

`TemplatesDialog` — lists templates; create from selected project / apply to project / delete

`SettingsDialog` — theme (light/dark), dense mode, show status column toggle, sync repo URL

`SplashDialog` — dev/prod mode selection at launch

**Shared helpers:**
- `UiHelper.iconButton` — label+icon button, goes icon-only in dense mode
- `UiHelper.iconOnlyButton` — always icon-only (breadcrumbs, compact contexts)
- `UiHelper.tagsRenderer()` — `DefaultTableCellRenderer` that renders `String[]{own, inherited}` with inherited tags in HTML italic

---

## Persistence (`namdesktop.persist`)

`JsonWorkspaceRepository` — reads/writes `WorkspaceFile` (Jackson). `save(null, …)` is a no-op (e2e in-memory mode).
Migration: `migrate()` creates missing structural nodes on load for backward compatibility.
Format version field present for future migrations.

---

## Sync (`namdesktop.sync`)

`GitSyncService` — push/pull workspace JSON via a bare Git repo. Post-pull dialog offers "Exit now" / "Later" since a restart is needed to apply the pulled workspace.

---

## Demo & E2E (`swingdemo`, `namdesktop.demo`)

**`swingdemo`** — reusable app-agnostic library: `ScriptRunner` drives a JSON action script on the Swing EDT; `ActionHandler` interface; `DemoStep` record; `RefreshBus` interface. Counts failures, fires `onStep`/`onComplete` callbacks.

**`NamDemoWiring`** — NamDesktop action handlers: `addProject`, `addSubProject`, `addAction`, `addNextAction`, `addInboxItem`, `markNext`, `markDone`, `markBacklog`, `addTag`, `createSavedView`, `deleteProject`

**`NamAssertWiring`** — assertion handlers (throw `IllegalStateException` on failure): `assertNodeExists`, `assertNodeNotExists`, `assertNodeStatus`, `assertTagOnNode`, `assertProjectExists`, `assertSavedViewExists`, `assertNodeCount`

**`demo.json`** — 30-step narrative GTD workspace demo; available in dev mode via File → Run Demo… (resets workspace first)

**`e2e.json`** — minimal build + assert script; `make e2e` runs it against a fresh workspace and exits 0/1

---

## Settings (`namdesktop.app`)

`AppSettings` — persisted to `~/.namdesktop/settings.json`: `theme`, `isDense`, `isShowStatusColumn`, `syncRepoUrl`

---

## Open issues of note

| # | Area | Notes |
|---|---|---|
| #39 | Sub-project support | "Make project" disabled when opening action from ProjectDialog |
| #160 | ActionDialog | Show inherited project tags as read-only in ActionDialog |

---

## What is settled vs still open

**Settled:**
- Layer structure (model → service → lens → UI → persist)
- NEXT/BACKLOG as status, not structure; `isProject` flag on node
- Query-time tag inheritance via `effectiveTags()` — removing a project tag affects all descendants immediately
- Structural node exclusion invariant across all lenses
- Context lens as primary "what can I do now?" surface; saved views as named filters with optional next-only toggle
- Manual ordering in Next Actions and Backlog views
- Project workbench as the primary project management surface
- Templates for creating project hierarchies
- Demo script (narrative) and e2e script (regression) as separate concerns sharing the same `swingdemo` machinery
- `make e2e` as the regression gate; e2e script updated alongside features

**Still open:**
- Sub-project classification rules (#39)
- Dialog navigation model — replace vs stack vs non-modal panel (#57)
- OR tag semantics in context lens (currently AND only)
- "Mission Control" / area-level views (vision doc)
