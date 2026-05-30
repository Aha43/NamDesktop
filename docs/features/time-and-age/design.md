# Feature: Time and Age (concept in progress)

Status: **exploratory** — shape is clear enough for a first issue set, but no issues created yet. Waiting for discussion to settle on visual treatment details.

## Motivation

NAM currently has no time awareness. The first real pull from use is **staleness** — not scheduling or deadlines, but the ability to see what has been sitting ignored. An inbox that grows old means processing is being skipped. A next action that hasn't been touched in weeks may not really be next. A backlog item from three months ago deserves a look.

Time enters as a quiet observer first, not as a scheduling demand. No date pickers, no due dates, no user burden.

## What is settled

### Timestamps on NamNode

Three fields added to `NamNode`:

| Field | Set when | Null means |
|---|---|---|
| `createdAt` | Node first created | Pre-timestamps data — treat as unknown, never as infinitely old |
| `updatedAt` | Any mutation OR node viewed/opened in a dialog | Unknown — not stale |
| `statusChangedAt` | Status transition only (NEXT/BACKLOG/DONE) | Never had a status change recorded |

**"Seen" counts as a touch.** Opening a node's dialog sets `updatedAt`. Staleness means "haven't even looked at it" — not just "haven't edited it."

**Null handling.** Existing nodes have no timestamps. Missing fields deserialise as `null`. Null is treated as unknown throughout — nodes with null timestamps do not appear in staleness-based views and show no age indicator. Never backfill or guess.

### Age indicator on rows

A small relative age label on each row in Inbox, Next Actions, and Backlog panels:

- Format: relative — `3d`, `2w`, `4m` — based on `updatedAt`, falling back to `createdAt` if `updatedAt` is null, blank if both null
- Visually subtle — muted color, small size, does not compete with title or status badge
- Inbox age carries slightly more visual weight than Next Actions / Backlog — an old inbox signals a processing failure, which is more urgent than a stale next action

### Sort by age — FIFO / LIFO toggle

Each of the three panels (Inbox, Next Actions, Backlog) gets a sort mode toggle:

- **FIFO** — oldest first; process in order, clear the backlog from the bottom
- **LIFO** — newest first; deal with what is fresh

Toggle is intentional (not just a column header click) — it represents a processing strategy choice, not a casual sort. Persisted per panel in `AppSettings` so it survives restart.

### Why these three panels

| Panel | Age meaning |
|---|---|
| Inbox | Processing failure signal — inbox should not accumulate |
| Next Actions | Commitment health — old next actions may not really be next |
| Backlog | Forgotten pile — oldest-first sort surfaces what has been ignored longest |

Context, Saved Views, Done, and the workbench do not show age in v1 — can be added later if use reveals a need.

## Future directions (not in scope now)

- Staleness lens: a dedicated view showing everything not touched in N days across all panels
- Scheduling: deferred-until, due dates — time as a filter, not just an observer
- Velocity: `statusChangedAt` enables "how long did this action take" — useful for estimation later
- Age on project nodes in the workbench

## Open questions

| Question | Notes |
|---|---|
| Exact visual treatment of age indicator | Size, color, placement relative to status badge and title — needs UX decision before dev chat |
| FIFO/LIFO toggle UI | Button, icon, or labelled toggle in panel toolbar — TBD |
| Threshold for visual emphasis | At what age does an indicator change weight — e.g. inbox items over 7 days get amber? Or keep it neutral throughout? |

## Dependencies

- No dependency on other feature epics — can be implemented independently
- Timestamps should be added early; age display and sorting can follow in subsequent issues
