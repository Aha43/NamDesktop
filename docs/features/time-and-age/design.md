# Feature: Time and Age

Status: **A sprint shipped** — #326 and #327 merged on `feature/time-a-sprint`. B sprint (due dates) follows after merge to main.

## Motivation

NAM currently has no time awareness. The first pull from real use is **staleness** — not scheduling, but the ability to see what has been sitting ignored. An inbox that grows old means processing is being skipped. A next action that hasn't been touched in weeks may not really be next.

Due pressure is the second pull: some work has deadlines. NAM should understand that pressure and surface it without turning into a calendar.

Time enters as a quiet observer first, then as optional pressure. No reminders, no calendar grid, no recurrence. NAM understands due dates and aging work. Calendar scheduling remains an integration concern.

## Scope

Six issues in two groups:

| Issue | # | Title |
|---|---|---|
| A1 | #326 | Age indicator column on Inbox, Next Actions, Backlog rows |
| A2 | #327 | FIFO/LIFO sort toggle for Inbox, Next Actions, Backlog |
| B1 | #328 | Add `dueAt` (LocalDate) to NamNode — model + persistence |
| B2 | #329 | Due date field in ActionDialog |
| B3 | #330 | Due hints column in Next Actions, Backlog, Context, SavedView panels |
| B4 | #331 | Due Actions panel: Overdue / Today / This week / Later |

## Out of scope

- Calendar grid
- Recurrence
- Reminders / notifications
- Time blocking
- External calendar sync
- `completedAt` — `statusChangedAt` at DONE covers v1; defer a separate `completedAt` field
- Due dates on projects (actions only in v1)
- Keyboard date shortcuts ("tomorrow", "next friday") in ActionDialog
- Snooze / dismiss / reschedule from Due Actions panel
- Sorting by due date within Next Actions, Backlog, Context panels (handled by the dedicated panel)
- Age / due hints in Context, SavedView, Done, workbench panels for v1 (except Due column in B3)

## What is already implemented

Three timestamp fields on `NamNode` as `LocalDateTime`:

| Field | Set when | Null means |
|---|---|---|
| `createdAt` | Node first created | Pre-timestamp data — treat as unknown |
| `updatedAt` | Any mutation OR node viewed/opened in a dialog | Unknown — not stale |
| `statusChangedAt` | Status transition only (NEXT/BACKLOG/DONE) | Never had a status change recorded |

**"Seen" counts as a touch.** Opening a node's dialog sets `updatedAt`. Staleness means "haven't even looked at it."

**Null handling.** Existing nodes have no timestamps. Missing fields deserialise as `null`. Null is treated as unknown throughout — no age indicator shown, no staleness assumption. Never backfill or guess.

## Design decisions

### Age indicator placement — narrow "Age" column

A narrow column labeled "Age", positioned between title and tags. Right-aligned value. Consistent with the existing "Status" column pattern; makes future sorting possible.

Inline-in-title was rejected: mixes signal with content, harder to scan.

### Age format and color

- Format: `3d`, `2w`, `4m`, `1y` — based on `updatedAt`, falling back to `createdAt`; blank if both null
- Color: muted (FlatLaf secondary/disabled text) for Next Actions and Backlog
- Inbox: items >7 days get amber — a processing failure signal, warrants slightly more emphasis

### FIFO/LIFO toggle

Icon button in each panel toolbar. Tooltip: "Oldest first" / "Newest first". Sort key: `updatedAt` → fallback `createdAt`; nodes with no timestamps sort last. Persisted per panel in AppSettings. Default: no sort applied (existing behavior preserved on first run).

Toggle is intentional (not a column header click) — it represents a processing strategy choice.

### `dueAt` field type — `LocalDate`

Date only. GTD due pressure is date-level ("due Friday"), not clock-level. Avoids timezone complexity. Can upgrade to `LocalDateTime` if reminders ever enter scope.

### Due column in action list panels

Narrow "Due" column in Next Actions, Backlog, Context, SavedView panels. Blank when no `dueAt`. Color-coded:

| State | Display | Color |
|---|---|---|
| Overdue | `2d ago` or short date | Red |
| Today | `Today` | Amber |
| This week (≤7 days) | Day name e.g. `Fri` | Muted blue |
| Later | Short date e.g. `Jul 3` | Default text |

Inline-in-title was rejected: same reasoning as Age column.

### Due Actions panel

New lens (`DueLens`) + panel. Scope: non-DONE, non-project nodes where `dueAt != null`. Four sections: Overdue, Today, This week, Later. Empty sections hidden. Rows use the same action row style as Next Actions (badge + title + project path + tags). Sorted by date within each section. No manual ordering.

Nav position: "Due" — after Backlog, before Done.

## Open questions

None remaining. All design decisions resolved.

## Dependencies

- B1 must land before B2, B3, B4
- A1 and A2 are independent of Group B
- No dependency on other feature epics
