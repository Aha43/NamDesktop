# Feature: Show / Hide Done Actions Inline

## Motivation

Done actions are currently only visible in the global Done panel. That panel is workspace-wide and loses project and context scoping. When working through a project or filtering by context, users naturally want to see what they have already completed alongside what is still pending — a checklist view. This toggle makes that possible without replacing the Done panel.

## Scope

Show/hide applies to three surfaces:

- **Context panel** — done actions matching the active tag filter
- **Saved Views panel** — done actions matching the saved view's tag filter
- **Project Workbench** — done actions in each sub-project section and the "This project" section

## Out of scope

- Next Actions and Backlog panels (forward-looking lists; done items are noise there)
- Due panel (pressure/priority surface; done items do not belong)
- Per-panel persistence — one global toggle, same state across all affected panels
- New visual treatment — the existing D badge already renders done items in grey; no additional styling needed
- MCP server — this is a display preference only; no API surface changes required

## Design decisions

### 1. Global setting

Add `showDoneInline: boolean` to `AppSettings`, default `false`. Persisted to `~/.namdesktop/settings.json` alongside existing settings.

### 2. Toggle button

Each affected panel (Context, Saved Views, Project Workbench) gets a toolbar toggle button. Clicking it flips `AppSettings.showDoneInline` and calls `refresh()` on the active panel. All three panels read the same setting, so toggling in one panel takes effect everywhere.

Icon suggestion: a checkmark or eye icon; icon-only in dense mode (consistent with `UiHelper.iconButton` pattern).

### 3. ContextLens — includeDone parameter

Extend `ContextLens` with an `includeDone` boolean parameter (passed through from the panel on each refresh). When `includeDone` is true, done actions that match the tag filter are appended after the active items.

Done items are included regardless of the `nextOnly` flag — `nextOnly` restricts BACKLOG items, not done ones. A user who has enabled `nextOnly` and toggles show-done should still see the done items.

### 4. ProjectWorkbenchLens — includeDone parameter

Extend `ProjectWorkbenchLens` (and its `WorkbenchProjection`) with an `includeDone` flag. When true, each sub-project section's action list appends done actions (`!isProject && status == DONE`) after the active actions. The "This project" section follows the same rule.

### 5. Ordering of done items

Done items appear at the bottom of their section's action list. Within the done group, order is unspecified (insertion order is fine). No separate "Done" group header — just appended at the bottom, distinguished by the existing D badge.

## Open questions

_None remaining — all product decisions resolved above._

## Help impact

Sprint-end checklist: update the Context and Project Workbench help articles to mention the show/hide done toggle.
