# NamDesktop — Current Implementation State

> This document describes the current implementation state, not the final architecture.
> Update it at the end of each sprint so design discussions stay grounded.

Last updated: 2026-05-20 (after sprints: #58 convert project→action, #60 tags, #61 context lens)

---

## Stack

Java 21 · Swing · No Maven/Gradle (plain Makefile) · FlatDarkLaf · Jackson · JUnit 5
Source: `src/namdesktop/` · Deps: `lib/` · Tests: `test/namdesktop/`

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

**NamNode** — `UUID id`, `String title`, `String description`, `NodeStatus status`, `List<UUID> childIds`, `List<String> tags`

**NodeStatus** — `NEXT | BACKLOG | DONE | CANCELLED | ARCHIVED`
- `@JsonAlias("ACTIVE")` on `NEXT` for backward compatibility with old saves
- New nodes default to `BACKLOG`; promotion to `NEXT` is explicit

**Tags** — plain strings, normalised to lowercase. Convention: `@context` for GTD contexts (`@computer`, `@home`, `@errands`), plain words for topics (`astronomy`, `family`). Not enforced by model.

**NamWorkspace** — `LinkedHashMap<UUID, NamNode> nodes` (insertion-order preserved)
Well-known UUIDs: `rootNodeId`, `inboxNodeId`, `projectsNodeId`, `nextActionsNodeId`

Key methods:
- `getNode(UUID)`, `getChildren(UUID)`, `getInboxItems()`
- `getParent(UUID)` — scans childId lists to find parent
- `buildPath(UUID) → List<NamNode>` — ordered path from root to node (breadcrumbs)
- `allTags() → List<String>` — sorted, deduplicated union of all tags across all nodes

`createDefault()` creates: root("NAM") → [Inbox, Projects, Actions]

---

## Service (`namdesktop.service`)

`NamWorkspaceService` owns all mutations:

| Method | Effect |
|---|---|
| `addChild` | Adds child node |
| `renameNode` | Renames |
| `deleteLeaf` | Deletes leaf only |
| `markDone / markNext / markBacklog` | Status transitions |
| `updateDescription` | Persists description |
| `addInboxItem` | Adds to inbox |
| `convertInboxItemToNextAction` | Moves to Actions area + sets NEXT |
| `convertInboxItemToProject` | Moves to Projects area |
| `convertNextActionToProject` | Moves from Actions to Projects |
| `convertProjectToAction` | Demotes leaf project back to action; top-level → moves to Actions area + NEXT; sub-project → stays in place + NEXT; throws if has children |
| `addTag(UUID, String)` | Normalises + deduplicates; saves only if changed |
| `removeTag(UUID, String)` | No-op if absent; saves only if changed |
| `updateTags(UUID, List<String>)` | Bulk replace; used by NodeDialog on Save |

---

## Lens projections (`namdesktop.lens`)

Stateless, return immutable view-model records. Structural nodes always excluded.

| Lens | Record | Filter |
|---|---|---|
| `InboxLens` | `InboxItemRow(id, title, status)` | children of inboxNodeId |
| `ProjectsLens` | `ProjectItemRow(id, title, status)` | children of projectsNodeId |
| `NextActionsLens` | `NextActionItemRow(id, title, status, parentTitle)` | `status == NEXT` |
| `BacklogLens` | `BacklogItemRow(id, title, status, parentTitle)` | `status == BACKLOG` |
| `ContextLens` | `ContextItemRow(id, title, status, parentTitle, tags)` | `status == NEXT` + all required tags present (AND) |

`parentTitle` — non-null when node is a child of a non-structural parent (i.e. a project).

**Key invariants**:
- `NEXT` status = "is a next action", not structural location
- A node with children is implicitly a project (drives `convertProjectToAction` block)
- Structural nodes (root/inbox/projects/actions area) excluded from all lens projections

---

## UI (`namdesktop.ui`)

**MainFrame** — left `NavigationPanel` + swappable `ContentArea` + `JMenuBar` (File → Exit)

Nav entries: Inbox · Projects · Next Actions · **Context** · Backlog · Raw Tree

**Panels** (each has `refresh()` passed as `Runnable onChanged` to dialogs):

| Panel | Columns / Notes |
|---|---|
| `InboxPanel` | Title, Status — right-click: add/rename/mark done/delete/process |
| `ProjectsPanel` | Title, Status |
| `NextActionsPanel` | Title, Project, Status |
| `ContextPanel` | Wrapping checkbox tag selector (AND filter) + table: Title, Project, Tags |
| `BacklogPanel` | Title, Project, Status |
| `TreePanel` | Raw node tree |

**Dialogs** (all `APPLICATION_MODAL`):

`NodeDialog` (base) — title, toolbar (status toggle + delete), description textarea, **tags field** (comma-separated, saved with Save), Save/Cancel
- `Runnable onChanged` — fired on every mutation before dispose
- Protected extension hooks: `addToolbarButton(JButton)`, `addBelowDescription(JComponent)`

`ActionDialog extends NodeDialog`
- `showMakeProject=true` → "Make project" + "Move to backlog" toolbar buttons
- If action has a non-structural parent → context row below description:
  - Label "Project: \<name\>" with breadcrumb tooltip (excl. root, e.g. `Projects > My Project`)
  - "Open project" button → closes dialog, opens `ProjectDialog` with action pre-selected

`ProjectDialog extends NodeDialog`
- Child action list: `JTable` (Title, Status), DONE rows grey
- Toolbar: "Convert to action" button (blocked with error if project has children) + "Add action" button
- Optional `initialSelection UUID` → scrolls to and selects that row on open
- Double-click child → `ActionDialog(showMakeProject=false)`

---

## Open issues of note

| # | Area | Notes |
|---|---|---|
| #39 | Sub-project support | "Make project" disabled when opening action from ProjectDialog; needs classification decisions |
| #57 | Dialog stacking UX | Navigating action↔project stacks modals unboundedly; cycle case is worst offender |

---

## What is settled vs still open

**Settled:**
- Layer structure (model → service → lens → UI → persist)
- NEXT/BACKLOG as status, not structure
- A node with children = implicitly a project (no explicit flag needed)
- Structural node exclusion invariant
- Lens architecture as the UI/domain boundary
- Tags as plain strings on NamNode; `@context` convention for GTD contexts
- Context lens as the primary "what can I do now?" surface

**Still open:**
- Sub-project classification rules (#39)
- Dialog navigation model — replace vs stack vs non-modal panel (#57)
- Saved/named context views (currently stateless filter)
- OR tag semantics in context lens (currently AND only)
- "Mission Control" / area-level views (future)
- View-specific ordering (vision doc §Ordering)
