# NamDesktop — Current Implementation State

> This document describes the current implementation state, not the final architecture.
> Update it at the end of each sprint so design discussions stay grounded.

Last updated: 2026-05-20 (after sprints: #63 tag autocomplete, #64 tag management, #66–#70 tag columns/polish/registry)

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

**Tags** — plain strings, normalised to lowercase. Convention: `@context` for GTD contexts (`@computer`, `@home`, `@errands`), plain words for topics. Not enforced by model.

**NamWorkspace** — `LinkedHashMap<UUID, NamNode> nodes` (insertion-order preserved)
Well-known UUIDs: `rootNodeId`, `inboxNodeId`, `projectsNodeId`, `nextActionsNodeId`
Tag registry: `List<String> registeredTags` — tags created upfront before any node uses them; persisted in `workspace.json`

Key methods:
- `getNode(UUID)`, `getChildren(UUID)`, `getInboxItems()`
- `getParent(UUID)` — scans childId lists to find parent
- `buildPath(UUID) → List<NamNode>` — ordered path from root to node (breadcrumbs)
- `allTags() → List<String>` — sorted, deduplicated union of `registeredTags` + all node tags

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
| `convertProjectToAction` | Demotes leaf project to action; top-level → moves to Actions area + NEXT; sub-project → stays in place + NEXT; throws if has children |
| `registerTag(String)` | Adds tag to registry (normalised, deduped); saves only if new |
| `addTag(UUID, String)` | Adds tag to node (normalised, deduped); saves only if changed |
| `removeTag(UUID, String)` | Removes tag from node; no-op if absent |
| `updateTags(UUID, List<String>)` | Bulk replace node tags; used by NodeDialog on Save |
| `renameTag(String, String)` | Renames tag across all nodes; deduplicates if target already present; saves once |
| `deleteTag(String)` | Removes tag from registry and all nodes; saves once if anything changed |

---

## Lens projections (`namdesktop.lens`)

Stateless, return immutable view-model records. Structural nodes always excluded.

| Lens | Record fields | Filter |
|---|---|---|
| `InboxLens` | `id, title, status` | children of inboxNodeId |
| `ProjectsLens` | `id, title, status, tags` | children of projectsNodeId |
| `NextActionsLens` | `id, title, status, parentTitle, tags` | `status == NEXT` |
| `BacklogLens` | `id, title, status, parentTitle, tags` | `status == BACKLOG` |
| `ContextLens` | `id, title, status, parentTitle, tags` | `status == NEXT` + all required tags present (AND) |

`parentTitle` — non-null when node is a child of a non-structural parent (i.e. a project).

**Key invariants**:
- `NEXT` status = "is a next action", not structural location
- A node with children is implicitly a project (drives `convertProjectToAction` block)
- Structural nodes (root/inbox/projects/actions area) excluded from all lens projections

---

## UI (`namdesktop.ui`)

**MainFrame** — left `NavigationPanel` + swappable `ContentArea` + `JMenuBar` (File → Manage Tags… · Exit)

Nav entries: Inbox · Projects · Next Actions · Context · Backlog · Raw Tree

**Panels** (each has `refresh()`):

| Panel | Columns |
|---|---|
| `InboxPanel` | Title, Status — right-click: add/rename/mark done/delete/process |
| `ProjectsPanel` | Title, Tags, Status |
| `NextActionsPanel` | Title, Project, Tags, Status |
| `ContextPanel` | Tag checkbox selector (AND, with match count + Clear button) + table: Title, Project, Tags |
| `BacklogPanel` | Title, Project, Tags, Status |
| `TreePanel` | Raw node tree |

**Dialogs** (all `APPLICATION_MODAL`):

`NodeDialog` (base) — title, toolbar (status toggle + delete), description textarea, `TagsField` (autocomplete, saved with Save button), Save/Cancel
- `Runnable onChanged` — fired on every mutation before dispose
- Layout: toolbar → [description + tags] inner panel → subclass content (SOUTH) via `addBelowDescription`
- Protected hooks: `addToolbarButton(JButton)`, `addBelowDescription(JComponent)`

`TagsField extends JTextField` — non-focus-stealing popup; suggests from `allTags()` filtering by current token (substring, case-insensitive); excludes already-present tags; arrow/Enter/Tab/Escape keyboard nav

`ActionDialog extends NodeDialog`
- `showMakeProject=true` → "Make project" + "Move to backlog" toolbar buttons
- If action has a non-structural parent → context row: label "Project: \<name\>" (breadcrumb tooltip excl. root) + "Open project" button

`ProjectDialog extends NodeDialog`
- Child list: `JTable` (Title, Tags, Status), DONE rows grey
- Toolbar: "Convert to action" + "Add action"
- Optional `initialSelection UUID` → scrolls to and selects row on open

`TagManagementDialog` — modal (File → Manage Tags…)
- Table: Tag, Used by (count)
- Toolbar: "New tag…" (adds to registry), "Rename…" (across all nodes), "Delete" (from registry + all nodes; gentler wording when count == 0)

---

## Open issues of note

| # | Area | Notes |
|---|---|---|
| #39 | Sub-project support | "Make project" disabled when opening action from ProjectDialog |
| #57 | Dialog stacking UX | Navigating action↔project stacks modals unboundedly |

---

## What is settled vs still open

**Settled:**
- Layer structure (model → service → lens → UI → persist)
- NEXT/BACKLOG as status, not structure
- A node with children = implicitly a project (no explicit flag needed)
- Structural node exclusion invariant
- Lens architecture as the UI/domain boundary
- Tags as plain strings; `@context` convention; registry for upfront tag creation
- Context lens as the primary "what can I do now?" surface
- Tag autocomplete, management (rename/delete), and columns throughout

**Still open:**
- Sub-project classification rules (#39)
- Dialog navigation model — replace vs stack vs non-modal panel (#57)
- Saved/named context views (currently stateless filter)
- OR tag semantics in context lens (currently AND only)
- "Mission Control" / area-level views (future)
- View-specific ordering (vision doc §Ordering)
