# Practice: Periodic Documentation Sprint

## What

A numbered doc sprint reviews and extends in-app help content and developer-facing docs as the app grows. Each sprint produces updated or renamed articles, new concept articles, and any README changes needed.

The first doc sprint (2026-05-31) produced issues #291–#294: focus-mode article rename, git sync article, AI assistant article, and GitHub link in About dialog.

## When to trigger

**Feature-based, not calendar-based.** The right moment is when enough new features have shipped that the help browser feels noticeably incomplete to a new user. In practice:

- After a major feature cluster lands (resources, library, time/age, AI integration, etc.)
- When a real user asks "how do I...?" about something that should be in the help browser
- When article names or terminology drift from the current UI (rename triggers)

Every 3-4 sprints is a natural rhythm, mirroring the UX review cadence.

## How to run it

1. Open a planning session (this chat).
2. Review `src/resources/help/` for stale names, missing articles, and outdated content.
3. Identify new articles needed based on features shipped since the last sprint.
4. Create GitHub issues labelled `enhancement` + `documentation`.
5. Note any intro or overview article updates triggered by the new articles — fold into the same sprint or flag as follow-up.

## Naming convention

| Artifact | Pattern |
|---|---|
| GitHub issues | labelled `enhancement` + `documentation` |
| Practice doc | this file — update "last sprint" note after each one |
| Article files | `src/resources/help/concepts/<slug>.html` |

## Articles needing attention (running list)

Update this list after each sprint so the next one starts with context.

| Article | Issue | Notes |
|---|---|---|
| `moon-cards.html` → `focus-mode.html` | #291 | Rename + rewrite |
| `mission-control.html` → `goal-board.html` | — | Not yet issued — follow-up |
| `contexts.html` → `tags.html` | — | Not yet issued — follow-up |
| `tutorials/planning-a-goal-with-mcr.html` | — | Stale terminology — follow-up |
| `concepts/git-sync.html` | #292 | New article |
| `concepts/ai-assistant.html` | #293 | New article |

## Reference

- [`docs/practices/ux-review.md`](ux-review.md) — parallel practice for UX reviews
