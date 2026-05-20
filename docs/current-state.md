# NamDesktop — Current Implementation State

> This document describes the current implementation state, not the final architecture.
> Update it at the end of each sprint so design discussions stay grounded.

Last updated: 2026-05-20 (after sprint: project/action UX — #38, #52, #53, #54, #55)

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

**NamNode** — `UUID id`, `String title`, `String description`, `NodeStatus status`, `List<UUID> childIds`

**NodeStatus** — `NEXT | BACKLOG | DONE | CANCELLED | ARCHIVED`
- `@JsonAlias("ACTIVE")` on `NEXT` for backward compatibility with old saves
- New nodes default to `BACKLOG`; promotion to `NEXT` is explicit

**NamWorkspace** — `LinkedHashMap<UUID, NamNode> nodes` (insertion-order preserved)
Well-known UUIDs: `rootNodeId`, `inboxNodeId`, `projectsNodeId`, `nextActionsNodeId`

Key methods:
- `getNode(UUID)`, `getChildren(UUID)`, `getInboxItems()`
- `getParent(UUID)` — scans childId lists to find parent
- `buildPath(UUID) → List<NamNode>` — ordered path from root to node (used for breadcrumbs)

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

---

## Lens projections (`namdesktop.lens`)

Stateless, return immutable view-model records. Structural nodes always excluded.

| Lens | Record | Filter |
|---|---|---|
| `InboxLens` | `InboxItemRow(id, title, status)` | children of inboxNodeId |
| `ProjectsLens` | `ProjectItemRow(id, title, status)` | children of projectsNodeId |
| `NextActionsLens` | `NextActionItemRow(id, title, status, parentTitle)` | `status == NEXT` |
| `BacklogLens` | `BacklogItemRow(id, title, status, parentTitle)` | `status == BACKLOG` |

`parentTitle` — non-null when action is a child of a non-structural node (i.e. a project).

**Key invariant**: `NEXT` status = "is a next action", not structural location. Project children can be `NEXT` without moving out of their project.

---

## UI (`namdesktop.ui`)

**MainFrame** — left `NavigationPanel` + swappable `ContentArea` + `JMenuBar` (File → Exit)

Nav entries: Inbox · Projects · Next Actions · Backlog · Raw Tree

**Panels** (each has `refresh()` passed as `Runnable onChanged` to dialogs):

| Panel | Columns |
|---|---|
| `InboxPanel` | Title, Status — right-click: add/rename/mark done/delete/process |
| `ProjectsPanel` | Title, Status |
| `NextActionsPanel` | Title, Project, Status |
| `BacklogPanel` | Title, Project, Status |
| `TreePanel` | Raw node tree |

**Dialogs** (all `APPLICATION_MODAL`):

`NodeDialog` (base) — title, toolbar (status toggle + delete), description textarea, Save/Cancel
- `Runnable onChanged` — fired on every mutation before dispose
- Protected extension hooks: `addToolbarButton(JButton)`, `addBelowDescription(JComponent)`

`ActionDialog extends NodeDialog`
- `showMakeProject=true` → "Make project" + "Move to backlog" toolbar buttons
- If action has a non-structural parent → context row below description:
  - Label "Project: \<name\>" with breadcrumb tooltip (excl. root, e.g. `Projects > My Project`)
  - "Open project" button → closes dialog, opens `ProjectDialog` with action pre-selected

`ProjectDialog extends NodeDialog`
- Child action list: `JTable` (Title, Status), DONE rows grey
- "Add action" toolbar button
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
- Structural node exclusion invariant
- Lens architecture as the UI/domain boundary

**Still open:**
- Sub-project classification rules
- Dialog navigation model (replace vs stack vs non-modal panel)
- "Mission Control" / area-level views (future)
