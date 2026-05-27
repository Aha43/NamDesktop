# Action Prerequisites (Blocked-By)

> Feature design note — ready for implementation.
> Start with issue #206 (model and service), then tackle the UI issues one at a time.

## What and why

Actions often cannot start until other actions are done. Without explicit modeling of this,
the Next Actions view fills with unactionable items and the user has to remember blocking
relationships in their head.

This feature lets the user say "action A cannot be done until B and C are done."
The data forms a DAG (directed acyclic graph). No graph editor is needed — the value comes
from filtering and feedback, not from visualising the structure.

## Design decisions (settled — do not re-litigate)

**Direction: prerequisites as a lightweight field on the action.**
`NamNode` gains a `blockedBy: List<UUID>` field. Nothing else in the model changes.
No new node types, no WAITING status, no sequential project mode (may come later as a
shortcut that auto-generates these links, but not now).

**Entry point: the blocked side.**
The user creates a prerequisite link from the action that is blocked — "I can't do A until
B is done" is entered in A's dialog. The blocking side (B's dialog) shows a read-only
"Would unblock" list for awareness, with links to navigate to the blocked actions.

**Removal: explicit, from the blocked side.**
Each entry in the Prerequisites list has a × remove button. No confirmation dialog needed —
this is just a link, not data deletion.

**Cycle detection: silent rejection at link-add time.**
If adding a link would create a cycle, the picker excludes that action from results.
No modal, no error dialog — the item simply does not appear as a valid choice.

**Next Actions and Backlog: hide blocked by default.**
Blocked actions are not actionable. They are hidden unless the user toggles the padlock
button in the toolbar.

## The five issues

| # | Scope | Depends on |
|---|---|---|
| [#206](https://github.com/Aha43/NamDesktop/issues/206) | Model, service, persistence | — (do this first) |
| [#207](https://github.com/Aha43/NamDesktop/issues/207) | ActionDialog: prerequisites UI | #206 |
| [#208](https://github.com/Aha43/NamDesktop/issues/208) | Next Actions + Backlog: hide blocked, padlock toggle | #206 |
| [#209](https://github.com/Aha43/NamDesktop/issues/209) | Completion nudge in status bar | #206 |
| [#210](https://github.com/Aha43/NamDesktop/issues/210) | Blocked lens: dedicated nav view grouped by blocker | #206 |

Tackle one issue per sprint in the order above. #207 and #208 are independent of each
other once #206 is done.

## Implementation sketch

### Model (`NamNode`)

```java
private List<UUID> blockedBy = new ArrayList<>();
// getters/setters as per existing style
```

JSON field name: `blockedBy`. Absent in existing files → deserializes as empty list
(Jackson default for `ArrayList`).

### Service (`NamWorkspaceService`)

```java
boolean addPrerequisite(UUID actionId, UUID prereqId)   // false = cycle detected
void    removePrerequisite(UUID actionId, UUID prereqId)
boolean isBlocked(UUID actionId)                         // any blockedBy not DONE?
List<UUID> unblocks(UUID prereqId)                       // reverse lookup
```

Cycle detection: depth-first walk of `blockedBy` from `prereqId` — if it reaches
`actionId`, reject. Iterative is fine; no workspace will have cycles deep enough to
overflow the stack, but iterative is safer.

Auto-cleanup on delete: `NamWorkspaceService.deleteNode(...)` (or wherever deletion
lives) must call a sweep — iterate all nodes and remove the deleted ID from their
`blockedBy` lists.

### UI: Prerequisites section in ActionDialog

Model the widget after `TagsField` — the existing tag autocomplete. The "Add prerequisite"
field searches by action title, filters out self and cycle-causing candidates, and calls
`addPrerequisite` on selection. Each existing entry renders as a label + × button that
calls `removePrerequisite`.

The read-only "Would unblock" section at the bottom of the same section calls `unblocks()`
and renders each result as a clickable link (same pattern as breadcrumb ancestor links).

### UI: padlock badge in action panels

Reuse the existing inline badge pattern (the colored status-letter badge). The padlock
badge sits in the same column, only visible when "show blocked" is toggled on.
Hover tooltip: "Blocked by: [title1], [title2]".

### Completion nudge

Hook into the existing DONE transition paths (toolbar button, status badge click,
ActionDialog save). After the transition, call `unblocks(completedId)`, filter for items
where `isBlocked()` is now false, and push a message to the status bar (add a simple
`JLabel` at the bottom of `MainFrame` if one does not already exist).

### Blocked lens (`BlockedLens`, `BlockedPanel`)

Follow the pattern of `DoneLens` / `DonePanel`. The lens returns a
`List<BlockedGroup>` where each group is `{blocker: NamNode, blocked: List<NamNode>}`.
An action blocked by N unmet prerequisites appears in N groups.

Nav entry: "Blocked", placed below Backlog in `NavigationPanel`.

## Key files to read before starting

- `src/namdesktop/model/NamNode.java` — add the field here
- `src/namdesktop/service/NamWorkspaceService.java` — add the four service methods
- `src/namdesktop/ui/TagsField.java` — model the search-and-link picker on this
- `src/namdesktop/ui/ActionDialog.java` — where the Prerequisites section goes
- `src/namdesktop/lens/DoneLens.java` + `src/namdesktop/ui/DonePanel.java` — template for the Blocked lens
- `src/namdesktop/ui/NavigationPanel.java` — where to add the Blocked nav entry
- `test/namdesktop/service/` — where unit tests for the service go
