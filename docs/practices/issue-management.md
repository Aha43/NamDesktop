# Practice: Issue Management

## Where issues are created

**Issues belong in the direction chat (ChatGPT / Claude direction session), not the dev chat.**

The direction chat thinks about scope, dependencies, out-of-scope items, and cross-references
before writing the issue. The dev chat implements what the issues say — it should not be
inventing scope or creating issues as a side effect of implementation.

Small things feel like they can be dashed off in the dev chat. Resist the habit. Even a
two-line issue benefits from being written here because:
- The spec is tighter when written with the full feature context in mind.
- Cross-references to related issues are correct.
- The label is applied from the start.
- The issue becomes part of the design record, not an afterthought.

## Labels

Every issue carries two labels:

1. **Type** — always `enhancement` for new features and improvements, `bug` for defects.
2. **Feature area** — identifies which coherent feature group the issue belongs to.

### Current feature area labels

| Label | What it covers |
|---|---|
| `prerequisites` | Action prerequisites and blocked-by dependency graph |
| `ai` | AI-powered capture and review via internal Anthropic API calls (#211–#214) |
| `external` | External agent integration: monitoring mode, MCP server, workspace file contract (#249–#251) |
| `cloud-sync` | Cloud database sync and remote persistence |
| `branding` | Logo, icon, visual identity, app presentation |
| `ux-review-1` | Findings from the first critical AI UX review (2026-05-27) |
| `help` | Help system content, layout, and UX |
| `ux` | Planned UX improvement sprints (#346–#347 show/hide done) |
| `nam-web` | Suite feature — mirrors or relates to NamWeb (see Cross-suite coordination) |
| `archived` | Set aside for reference — superseded or deferred, intentionally not deleted |

When a new coherent feature area appears, create a label for it here and in GitHub at the
same time. Use a distinct colour so the label is visually distinguishable in the issue list.

`archived` issues are closed as **not planned** (not "completed"), so they drop out of the
open list but stay recoverable: `gh issue list --label archived --state closed`.

### Filtering

```bash
gh issue list --label prerequisites
gh issue list --label ai
gh issue list --label ux-review-1 --state open
```

## Experimental branches

Some work is exploratory enough that it should not flow through the normal
`feature/*` sprint cycle. For these we use long-lived `experiment/*` branches
(first one: `experiment/cloud` — backend + UI cloud-sync experiments).

Rules:

- Normal sprint work continues on `feature/*` branches off `main`, unaffected.
- `main` is merged **into** the experiment branch periodically to keep it current.
- Work flows back to `main` only selectively, when a piece has proven itself —
  not as a wholesale merge at a deadline.
- Issue discipline is unchanged on experiment branches: every commit belongs to an
  issue, `Closes #N` in commit messages, one issue at a time. Issues auto-close only
  if/when the work reaches `main`.
- Docs-only commits (design docs, practices) go directly on `main` as usual, so both
  branch lines see them.

## Cross-references

Issues within a feature group should reference all sibling issues in a "Related issues"
section. This makes the dependency chain visible without sub-issues.

We do not use sub-issues. Labels and cross-references are sufficient for grouping.

## Cross-suite (NamWeb) coordination

NamDesktop and **NamWeb** (`Aha43/NamWeb`) are one product suite. They are separate
repositories that share **only the Supabase HTTP contract** — no application code. The
database migrations are NamDesktop's single source of truth (`supabase/`). The goal is to
keep the two apps **functionally in sync**: a capability shipped on one should be mirrored
on the other, or consciously declined. Web usually leads; desktop occasionally leads
(heavy/algorithmic work proven here before committing to web).

When an issue has suite relevance:

- Label it `nam-web` — the mirror of NamWeb's `nam-desktop` label.
- Link the counterpart issue as `Aha43/NamWeb#NNN` in the body, and note which app leads /
  ship order ("web ships first").
- `gh issue list --label nam-web` is the desktop parity worklist.

**The CHANGELOG is the parity ledger.** When an entry mirrors the other app, cite the
counterpart issue in the entry itself (e.g. "Parity with `Aha43/NamWeb#167`"). The two
CHANGELOGs then cross-check: a parity feature on one side with no counterpart on the other
is drift you can spot mechanically. When a desktop feature lands that web should mirror,
file or reference the matching `nam-desktop` issue on NamWeb (and comment the result back
when the loop closes).

**Suite sync sweep (per sprint).** Reconcile each repo's recently-closed `nam-web` /
`nam-desktop` issues against the other's CHANGELOG. For each feature shipped on one side
without a counterpart on the other, either file a mirror issue or close it as won't-follow —
so drift is always an explicit decision, never silent. Backend/Supabase changes always land
in NamDesktop first.

## Issue content

Follow the `improvement` template shape: **What / Why / Suggested behavior / Notes**.

- **What** — one sentence.
- **Why** — the motivation. Future readers should understand why this was worth doing.
- **Suggested behavior** — specific enough that the dev chat can implement without
  guessing. Include method names, UI placement, edge cases.
- **Notes** — dependencies, out-of-scope items, implementation hints.

Out-of-scope items are as important as in-scope ones. State them explicitly so the dev
chat does not over-implement.

## Design decisions

When a deeper issue requires a product decision before implementation (naming, model
identity, flow design), resolve the decision in the direction chat and record it as
an issue comment before handing to the dev chat. The dev chat reads the issue comments.

See `docs/practices/ux-review.md` for how this works in the context of UX reviews.
