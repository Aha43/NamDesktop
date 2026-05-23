# Ideas

A scratchpad for future directions — one-liners from any contributor (Arne, ChatGPT, Claude Code).
No commitment, no priority order. Raw material for planning sessions.

## Ordering (multi-phase, vision.md §Ordering)

The same node may need a different position in different views. Three kinds of order exist:

1. **Structural order** — the `childIds` list on a node; already persisted; defines sibling order under a parent.
2. **View-specific manual order** — the order a user has chosen within a particular lens (Next Actions, Backlog, a saved view, a project's action list in the workbench). Must be persisted separately as a `Map<viewKey, List<UUID>>` on the workspace and reconciled on open: drop IDs no longer present, append new arrivals, keep the rest in saved order.
3. **Computed fallback order** — default sort (e.g. insertion order) for items not yet manually sorted.

### Phase 1 — structural reordering (small sprint)
- Up/down buttons in the workbench action lists and project sections
- Manipulates `childIds` directly — no new model concepts
- Same buttons in raw tree context menu (move node up/down among siblings)

### Phase 2 — view-specific ordering (model sprint)
- Add `viewOrders: Map<String, List<UUID>>` to `NamWorkspace` and persist it
- Write a `ViewOrderReconciler` utility: given saved order + current item set → reconciled list
- Apply to Next Actions panel and Backlog panel first (simplest fixed-key views)
- Up/down buttons or drag-and-drop in those panels

### Phase 3 — per-context ordering (follow-on)
- Apply view-specific ordering to saved views and context filter results
- Each saved view gets its own key in `viewOrders`
- Project workbench action lists get per-project keys (actions in "Japan trip" ordered independently from the same actions appearing in Next Actions)

### Notes
- Phase 2's reconciler is the reusable core — worth investing in tests
- Drag-and-drop is a UX upgrade on top of up/down; can be added per-panel independently
- Humans love ordering things; even Phase 1 alone will make the app noticeably more pleasant

## Workbench

- Mark actions done directly in the workbench list without opening ActionDialog
- Keyboard navigation in workbench (arrow keys, Enter to open action)

## Next Actions / Backlog panels

- Inline mark-done without opening ActionDialog
- Direct action creation with tag pre-fill from current context filter

## Data / structure

- Move a node between projects via context menu or drag
- Orphan detection and repair tool in dev mode

## UX / onboarding

- What a new user sees on first launch — guided empty state
- Sample workspace for demo / testing

## Longer term

- Use NamDesktop itself to manage NamDesktop backlog (dog-fooding)
