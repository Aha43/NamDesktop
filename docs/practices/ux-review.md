# Practice: Periodic Critical UX Review

## What

A numbered critical AI UX review of the app, done periodically as new UI surface
accumulates. Each review produces GitHub issues, recorded decisions, and a design doc.

The first review (`ux-review-1`, 2026-05-27) produced 14 issues — 8 quick wins and
6 deeper changes — all resolved in one day. The spec quality meant the dev chat
implemented correctly with minimal back-and-forth.

## When to trigger

**Feature-based, not calendar-based.** The right moment is when enough new UI surface
has appeared that fresh eyes would find things. In practice:

- After a major feature cluster lands (Goal Board, AI integration, cloud sync, etc.)
- When a real user gets confused somewhere specific
- When the app feels stable enough to zoom out from implementation

Every 3-4 sprints is a natural rhythm. Weekly during heavy development produces
diminishing returns — you end up reviewing the same surfaces repeatedly.

## How to run it

1. Open a direction session (this chat, not the dev chat).
2. Ask for a brutal UX review — name the areas to focus on if known, otherwise let it
   range freely.
3. The review produces: overarching problems, per-area findings, quick wins, and deeper
   changes. Quick wins are implementation-ready. Deeper changes need a product decision
   recorded here before the dev chat touches them.
4. Create GitHub issues from the findings, all labelled `ux-review-N`.
5. Resolve product decisions (naming, identity questions, flow design) in this chat,
   recording each decision as an issue comment.
6. Create `docs/features/ux-review-N/design.md` summarising all findings, decisions,
   and outcomes. Update it to "completed" when all issues are closed.

## Naming convention

| Artifact | Pattern |
|---|---|
| GitHub label | `ux-review-1`, `ux-review-2`, … |
| Design doc | `docs/features/ux-review-N/design.md` |
| Issue cross-references | Every issue lists all sibling issues in "Related issues" |

## Why numbered reviews matter

Each completed review is a permanent record of how the product's UX reasoning evolved.
Future contributors (or future you) can read `ux-review-1` through `ux-review-N` and
understand exactly why things look the way they do — not just what changed, but why.

## Reference

- [`docs/features/ux-review-1/design.md`](../features/ux-review-1/design.md) — first
  review, completed 2026-05-28. 14 issues. Key decisions: Backlog identity (Option A),
  Goal Board, Focus mode, Tags, Saved Filters, Readiness view, processed flag on NamNode.
