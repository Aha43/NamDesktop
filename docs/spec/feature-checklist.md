# NamDesktop — End-User Capability Spec

A complete, grouped list of what an end user can do in NamDesktop. Purpose:
regression testing, and a parity checklist for verifying the web version covers
the desktop feature set.

Source of truth is the code (nav entries, menus, dialogs, MCP tools); this list
is reconciled against `CHANGELOG.md`. Two areas are summarized loosely and should
be re-verified against source before treating as authoritative: the exact current
MCP tool roster, and the Readiness / Goal-Board view overlap.

---

## 1. Launch, modes & workspace

- **Splash on startup** — choose dev mode (checkbox) then Launch; closing splash exits the app.
- **Dev mode** — separate workspace (`~/.namdesktop/dev/workspace.json`), `[DEV]` in title bar and amber `● Dev` toolbar badge; Git sync disabled; Raw Tree nav appears.
- **First-run welcome** — "Explore demo workspace" or "Start fresh"; shown once, persisted.
- **Run Demo…** (File menu, always available) — builds a realistic sample workspace; warns before overwriting existing data.
- **Workspace auto-saves locally**; data lives in `workspace.json`.

## 2. Capture (Inbox)

- **Capture to Inbox** from anywhere — `Cmd/Ctrl+I`, toolbar button, or menu.
- **Bulk capture** — multiline dialog, one item per line (`Ctrl+Enter` confirms, `Enter` = newline).
- **Inbox panel**: add item (`+`), rename, mark done, delete (right-click).
- **Process an inbox item** — two-step dialog: Action vs Project; action path then "Do it next" vs "Park for later (Backlog)".
- Age indicator column (`3d`, `2w`…); items >7 days shown amber.
- FIFO/LIFO sort toggle (none → oldest → newest), persisted per panel.

## 3. Actions & statuses

- Status model: **Next / Backlog / Done** (new actions default to Backlog).
- **Inline status badge** (N/B/D) in all action lists — click for instant Next/Backlog/Done switch.
- **Inline rename** — click an already-selected title to edit in place (governed by "Click to rename" setting).
- **Enter** opens the edit dialog on the selected row; double-click also opens it.
- **ActionDialog**: edit title, description (`Ctrl+Enter` saves), tags, Backlog/Next/Done radios, due date, blocked-by, resources; Make project; Move to backlog; Move to…; Open project.
- **Add action directly** (no inbox) in Next Actions, Backlog, Tag filter, Saved View, and Project Workbench; bulk multiline add in Next Actions and Workbench.
- **Reorder** actions up/down (view-specific order persisted independently of tree).
- `Cmd/Ctrl+N` — context-sensitive new item (inbox item / project / action depending on panel).

## 4. Lenses / panels (left-nav)

- **Inbox**, **Projects**, **Next Actions**, **Tag filter** (Context), **Backlog**, **Blocked**, **Due**, **Done**; **Raw Tree** (dev only).
- **Next Actions / Backlog**: status column toggle, due-hint column, age column, FIFO/LIFO, blocked-actions hidden by default with padlock toggle.
- **Backlog**: free-actions-only toggle; top-level project filter chips (OR).
- **Blocked**: actions with unmet prerequisites, grouped by blocker.
- **Due**: non-done actions with due dates grouped Overdue / Today / This week / Later.
- **Done**: list of completed actions; Delete (confirm), Mark as Next, Mark as Backlog (resurrect, confirm); selection preserved after action.
- Full project path shown in the Project column across all action lists.
- Paperclip indicator where a node has resources.

## 5. Projects & Project Workbench

- **Projects panel**: add project directly; tag filter strip (OR, session-only).
- **Project Workbench** — action-forward surface: breadcrumb nav, "This project" actions + one section per sub-project; drill into sub-projects; edit ancestors inline (power mode).
- **Three views** via breadcrumb switcher: **Workbench / Columns / Readiness** — remembered per project across restarts.
- **Column view**: each sub-project = a column (+ leading "Unsorted"); cards = actions.
  - Drag a card to reparent (between columns) or reorder (within column); move button / right-click menu alternatives.
  - Collapse columns (chevron → narrow strip), remembered per project.
  - Rearrange columns (◀ ▶) — reorders sibling sub-projects; "Unsorted" stays pinned first.
  - Lanes button cycles: actions-only / actions + sub-projects / sub-projects-only; double-click sub-project to drill in.
- **Make project** — convert an action to a sub-project in place; **Convert to action** — demote a leaf project.
- **Move node** — move actions between projects / to free actions; move projects to any parent or top-level (via dialogs).
- **Recursive delete** — confirmation shows blast radius (N sub-projects, M actions).
- **ProjectDialog**: metadata editor (title, description, tags, resources, move, templates, delete).

## 6. Templates

- Save any project (full subtree) as a named template.
- Apply a template when converting an inbox item to a project.
- Manage templates (list/delete) via **File → Templates…**.

## 7. Tags

- Tags field with **autocomplete** (existing + registered tags); tags column in lists.
- Actions **inherit ancestor-project tags** at query time (shown in italic).
- **Tag filter (Context)** lens — filter NEXT/active actions by tags (AND across checked); match count + Clear; add tagged action.
- **Manage Tags…** dialog — list with usage counts, rename (cascades), delete; "New tag…" registers a tag up front.

## 8. Saved Views

- Save a tag filter as a named view → appears in nav under "Saved Views".
- "Next actions only" toggle (default off); rename view in place; delete view (confirm).

## 9. Goal Board (formerly Mission Control)

- Create a Goal Board from one or more tags (OR matching, dedup of nested tagged projects).
- Heat-map grid of station cards (red/amber/green by done ratio), rolled-up sub-project stats.
- Click a card → Project Workbench with board name in breadcrumb; delete board.
- Readiness/MCR view inside a project shows sub-projects as heat-map station cards.

## 10. Focus mode (Moon Cards / deck)

- Full-screen one-card-at-a-time view for Next Actions, Backlog, Saved Views.
- Previous/Next (circular), Mark Done & advance, Esc/Exit; "All done!" end state (`0 / 0`).

## 11. Due dates

- Set/clear a due date (ISO `YYYY-MM-DD`) in ActionDialog with inline validation.
- Due-hint column across action lists: overdue (red), today (amber), this week (blue), later.

## 12. Blocked-by / prerequisites

- Link prerequisite actions in ActionDialog (autocomplete, cycle-safe); "Would unblock" links.
- Blocked actions hidden by default in Next/Backlog; padlock reveals (grayed, tooltip lists blockers).
- Completion nudge — marking Done surfaces "Unblocked: …" in status bar (4s).
- Deleting a node sweeps its ID from all blocked-by lists.

## 13. Resources (attachments)

- Four types: URI / Email / File / Text; add with optional note; remove with ✕.
- Click to open (browser / mailto / file manager / clipboard); collapsible section in Action/Project dialogs.

## 14. Search

- **Search panel** (`Cmd/Ctrl+F`) — live substring match on title & tags; Title/Type/Project results; double-click opens dialog.

## 15. Navigation & window

- **Back** — toolbar button + `Cmd/Ctrl+[`; capped history (20); skips deleted projects.
- Re-clicking a nav item returns to that section's root.
- Quick jumps: `Cmd/Ctrl+1…5` → Inbox, Next Actions, Backlog, Projects, Done.
- **View menu**: Hide/Show Toolbar (`Cmd+T`), Hide/Show Nav Pane (`Cmd+Shift+N`), **Zen Mode** (`Cmd+Shift+Z`, hides both).

## 16. Settings (Appearance / Workspace / Sync)

- Sidebar layout, resizable dialog; reachable any time via File → Settings…
- **Theme** (Dark/Light, live), **Dense mode**, **Click to rename**, **Always show status column**, **Power user mode**.
- Persisted to `~/.namdesktop/settings.json`.

## 17. Sync

- **Git sync** — repo URL in Settings; Push/Pull (toolbar + File menu, background thread); `Cmd/Ctrl+S` pushes or nudges; hidden in dev mode.
- **Cloud sync (Supabase)** — enable toggle, URL/key/email/password in Settings; Push/Pull dispatch to Supabase when enabled (Cloud wins over Git); status "Synced — [time] (vN)"; conflict dialog (Keep remote / Keep local / Cancel); dev-mode targets a separate `dev` workspace.

## 18. AI agent integration (MCP) & Monitoring mode

- **Monitoring mode** (`Cmd+Shift+M`, toolbar antenna, amber `● Monitoring`) — mirrors workspace to `workspace.external.json` for an agent; live panel refresh; accept/reject summary on exit, checkpoint, or app quit; mutating UI actions prompt while active.
- **Checkpoint** (`Cmd+Shift+S`) — flush accepted changes, keep monitoring.
- **MCP server** (separate process) — read tools (workspace context, inbox, projects, next actions, backlog, done, resources, saved views, find/locate, project children, monitoring status) and write tools (add inbox item/action, create project, mark next/backlog/done, update/delete/move node, add/remove blocked-by, add/edit/remove resource); writes need monitoring mode (or `--direct`).

## 19. Help & About

- **Help** (`F1`) — three-pane browser; sidebar sections: Tutorial, Daily workflow, Projects, Finding work, App, Superpower (AI assistant), Background (GTD); concept links (`concept://`); pop-out floating window (persisted position/size).
- **Keyboard Shortcuts…** (`Cmd+/`) — sectioned reference.
- **About** — wordmark + version.
