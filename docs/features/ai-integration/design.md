# AI Integration: Capture and Weekly Review

> Feature design note — ready for implementation.
> Start with #211 (settings), then #212 (serializer), then #213 and #214 independently.

## What and why

Two distinct AI-powered capabilities, unified by one design principle:
**the AI returns structured, actionable workspace commands — not free text to read.**

**AI Capture** — the user types or pastes freeform text (brain dump, meeting notes,
voice transcript). The AI parses it into structured action suggestions. The user reviews
each suggestion and accepts or skips. Accepted items land in the Inbox.

**AI Weekly Review** — one button sends the full workspace inventory to the AI for an
Allen-style analysis. The AI returns structured recommendations: close this, mark this
next, this project has no next action, these items belong together as a project. The user
steps through each and accepts or skips. Accepting executes the workspace command
immediately.

Both features share the same underlying shape: AI call → structured response → human
triage one item at a time. The weekly review can also spawn new items (SUGGEST_PROJECT).

## Design decisions (settled)

**Internal API calls.** API key stored in AppSettings, app calls the Anthropic API
directly via `java.net.http.HttpClient` + Jackson (already a dependency). No new libs.
External (paste into Claude.ai manually) kills the habit — friction is the whole problem.

**Inbox-first capture.** Accepted capture items land as BACKLOG in the Inbox, consistent
with GTD capture-then-process. The AI suggests project placement but the item goes to
Inbox regardless; the user processes it in the normal flow. This is the right call because
capture should be fast and low-stakes.

**Structured AI responses.** Both prompts ask the model to return JSON. Parse with
Jackson. Never render raw model output directly into the workspace.

**Prompt templates in `AiPrompts`.** All prompt strings live in one class for easy
tuning without touching logic code.

**Haiku as default model.** Fast and cheap for the structured extraction tasks here.
User can change in settings.

## The four issues

| # | Scope | Depends on |
|---|---|---|
| [#211](https://github.com/Aha43/NamDesktop/issues/211) | AI settings: API key, model, enable toggle | — (do first) |
| [#212](https://github.com/Aha43/NamDesktop/issues/212) | Workspace AI serializer: clean text snapshot | #211 |
| [#213](https://github.com/Aha43/NamDesktop/issues/213) | AI capture button in Inbox panel | #211, #212 |
| [#214](https://github.com/Aha43/NamDesktop/issues/214) | AI weekly review with accept/skip recommendations | #211, #212 |

#213 and #214 are independent of each other once #211 and #212 are done.

## Implementation sketch

### Package layout

New package `namdesktop.ai`:
- `AiSettings` — record mirroring the JSON shape
- `WorkspaceAiSerializer` — two methods: `projectContext()` and `fullReview()`
- `AiCaptureClient` — wraps the API call for capture, returns `List<AiCaptureSuggestion>`
- `AiReviewClient` — wraps the API call for review, returns `List<AiRecommendation>`
- `AiPrompts` — all prompt template strings as constants
- `AiCaptureSuggestion` — record: title, description, suggestedProject, suggestedTags, suggestedStatus
- `AiRecommendation` — record: nodeTitle, type (enum), rationale, suggestedTitle, suggestedChildren

### API call pattern

No new library. Follow the existing `GitSyncService` pattern for HTTP, but simpler:

```java
var request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.anthropic.com/v1/messages"))
    .header("x-api-key", settings.apiKey())
    .header("anthropic-version", "2023-06-01")
    .header("content-type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
    .build();
var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
// parse response.body() with Jackson
```

The request JSON body: standard Messages API shape — `model`, `max_tokens`,
`messages: [{role: user, content: <prompt>}]`.

### Workspace serializer output

`projectContext()` — compact list for capture prompts:
```
Projects: Alpha Project [work, q2], Home repairs [home], Personal admin
Tags in use: work, home, q2, admin, health
```

`fullReview()` — full inventory for review prompts (see issue #212 for full example).
Individual action titles shown only for Inbox and unparented items to keep token count
manageable. Counts everywhere else.

### Capture prompt shape

```
You are a GTD assistant. The user has provided freeform notes below.
Extract individual tasks and return a JSON array of objects with fields:
title, description (optional), suggestedProject (match from list below or null),
suggestedTags (array, match from list below), suggestedStatus (NEXT or BACKLOG).

Existing projects: <projectContext()>

User notes:
<user input>
```

### Review prompt shape

```
You are a GTD coach doing a weekly review of this workspace.
Return a JSON array of recommendations. Each object must have:
  nodeTitle (string), type (one of: CLOSE, DELETE, MARK_NEXT, DEFER,
  NEEDS_NEXT_ACTION, SUGGEST_PROJECT), rationale (one sentence),
  and optionally suggestedTitle and suggestedChildren (for SUGGEST_PROJECT).

Focus on: stale backlog items, projects with no next action, inbox items
that have been sitting unprocessed, actions that look redundant or completed.

Workspace:
<fullReview()>
```

### Capture UI flow

`InboxPanel` toolbar → sparkle button → `AiCaptureDialog` opens.

Dialog has two phases (swap panel content, same window):
1. **Input**: text area + "Capture" button + spinner.
2. **Review**: scrollable list of `AiCaptureSuggestion` rows. Each row:
   editable title field, project badge (click to change), tag chips, status toggle,
   Accept (✓) / Skip (✗) buttons. "Accept all" at the bottom.

Accepted item: call `workspaceService.addInboxItem(title, description)` — always Inbox
regardless of suggested project. Project suggestion shown as a hint but not auto-applied;
user processes that in the normal workbench flow.

### Review UI flow

File menu "Weekly Review…" + toolbar button → `AiReviewPanel` shown in main content area
(same pattern as `HelpPanel` taking over the content area).

Scrollable list of `AiRecommendation` rows. Each row shows: node title (bold), action
type badge (colour-coded), rationale text. Buttons: **Accept** / **Skip** / **Open**.

Action type → command:
| Type | Command |
|---|---|
| CLOSE / DELETE | `deleteNode` (DELETE shows confirmation) |
| MARK\_NEXT | `markNextAction` |
| DEFER | `markBacklog` |
| NEEDS\_NEXT\_ACTION | opens `ProjectDialog` for that project |
| SUGGEST\_PROJECT | preview dialog → accept creates project + moves actions |

`SUGGEST_PROJECT` is the most complex — implement last, possibly in a follow-up issue.
The other four types are straightforward single-node commands.

Summary shown after all recommendations are processed or skipped:
*"Review complete — 8 accepted, 3 skipped."*

## Key files to read before starting

- `src/namdesktop/app/AppSettings.java` — add `AiSettings` record here
- `src/namdesktop/ui/SettingsDialog.java` + `SettingsPanel.java` — add AI tab
- `src/namdesktop/sync/GitSyncService.java` — HTTP client pattern to follow
- `src/namdesktop/ui/InboxPanel.java` — where the capture button goes
- `src/namdesktop/ui/HelpPanel.java` — pattern for a panel that takes over the content area
- `src/namdesktop/ui/MoonCardPanel.java` — card-by-card UX pattern (reference for review if switching to that style later)
- `src/namdesktop/lens/DoneLens.java` — lens pattern for `BlockedLens` analogue if needed
