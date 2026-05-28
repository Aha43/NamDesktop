# UX Review 1 — Critical AI Review Findings

> Source: first brutal AI UX review of NamDesktop, 2026-05-27.
> **Status: completed 2026-05-28.** All 14 issues resolved in one day.
> All issues carry the `ux-review-1` label for easy filtering.

## The three-language problem (root cause)

NamDesktop was speaking three dialects simultaneously:

1. **GTD vocabulary** — Inbox, Next Actions, Contexts (assumes the user has read Allen)
2. **NAM internal vocabulary** — Mission Control, Moon Cards, MCR, Workbench, station cards
3. **Implementation vocabulary** — status names as button labels, processing semantics
   leaking into UI copy

Every issue below traced back to one of these three.

---

## Quick wins — completed

| # | What | Outcome |
|---|---|---|
| [#219](https://github.com/Aha43/NamDesktop/issues/219) | MCR → "Readiness view"; "Saved Views" → "Saved Filters" | Done |
| [#220](https://github.com/Aha43/NamDesktop/issues/220) | Status badge legibility; Done panel button labels; Goal Board colour legend | Done |
| [#221](https://github.com/Aha43/NamDesktop/issues/221) | Moon Cards → **Focus mode** | Done |
| [#222](https://github.com/Aha43/NamDesktop/issues/222) | Inbox: Process as primary toolbar button | Done |
| [#223](https://github.com/Aha43/NamDesktop/issues/223) | Empty state copy on every action panel | Done |
| [#224](https://github.com/Aha43/NamDesktop/issues/224) | Search: scope label, pre-search and no-results states | Done |
| [#225](https://github.com/Aha43/NamDesktop/issues/225) | Backlog: inbox items excluded from lens (resolved with #229) | Done |
| [#226](https://github.com/Aha43/NamDesktop/issues/226) | Demo accessible on first launch outside dev mode | Done |

---

## Deeper changes — completed

| # | What | Decision | Outcome |
|---|---|---|---|
| [#227](https://github.com/Aha43/NamDesktop/issues/227) | Workbench: reduce overlapping edit affordances | Progressive disclosure, not removal | Branch merged. Follow-up in [#235](https://github.com/Aha43/NamDesktop/issues/235). |
| [#228](https://github.com/Aha43/NamDesktop/issues/228) | Inbox processing flow redesign | Three-outcome dialog: Do it next / Park it / Project. `processed` flag on NamNode. | Done |
| [#229](https://github.com/Aha43/NamDesktop/issues/229) | Backlog identity | **Option A** — Backlog = consciously deferred only | Done |
| [#230](https://github.com/Aha43/NamDesktop/issues/230) | Mission Control → **Goal Board** | Name: Goal Board. Station cards → goal cards. | Done |
| [#231](https://github.com/Aha43/NamDesktop/issues/231) | Context panel → **Tags** | Name: Tags | Done |
| [#232](https://github.com/Aha43/NamDesktop/issues/232) | First-run experience | Guided onboarding, no GTD knowledge required | Done |

---

## Open follow-ups

| # | What |
|---|---|
| [#235](https://github.com/Aha43/NamDesktop/issues/235) | Workbench: progressive disclosure for power-user shortcuts (future sprint) |

---

## Key decisions made in this review

- **Backlog** = consciously deferred only. Inbox items stay in Inbox until processed.
  Processing produces Next Action, Backlog item, or Project — never leaves an item in limbo.
- **Goal Board** replaces Mission Control. Goal cards replace station cards.
- **Focus mode** replaces Moon Cards.
- **Tags** replaces Context in the nav.
- **Saved Filters** replaces Saved Views.
- **Readiness view** replaces MCR.
- Workbench inline affordances are preserved — goal is progressive disclosure, not removal.
