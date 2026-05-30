# Feature: Resources

Status: **planned** — issues #275–#279 open, not yet started.

## Motivation

When working on an action, support material accumulates: store links when researching a purchase, file paths for reference documents, email addresses for contacts. Today there is nowhere to put this material inside NAM — it lives in a browser history, a separate notes app, or not at all.

Resources give actions and projects a lightweight, typed attachment list so support material stays where the work is. The primary real-world trigger: a human creates an action ("buy X"), an AI agent (Claude via MCP) researches candidates and populates URI resources — store links, product pages — directly onto the action.

## Scope

- `Resource` record: `type` (enum), `value` (string), `description` (optional string shown as tooltip)
- `ResourceType` enum: `TEXT | EMAIL | URI | FILE` — easy to extend
- `List<Resource>` on `NamNode`; persisted via Jackson
- Service methods: `addResource`, `removeResource`
- ActionDialog: collapsible section below tags — hidden when empty, expanded when populated
- ProjectDialog: same pattern as ActionDialog
- List views: paperclip icon on rows with ≥1 resource
- MCP server: `list_resources` (read), `add_resource` and `remove_resource` (write, monitoring mode)

## Out of scope (v1)

- Inbox items excluded from UI (model has no restriction — past-clarification heuristic)
- Resource ordering and archive status
- "Actions produce resources" (output resources) — separate future concept
- File browser button (paste/type path in v1)
- Resource count badge in list views (paperclip presence only)
- Inline editing of existing resources (delete + re-add)

## Design decisions

| Decision | Choice | Reason |
|---|---|---|
| Type representation | Enum (`ResourceType`) | Extensible, validated, no typos |
| Default type in Add form | URI | Primary use case |
| TEXT click behaviour | Copy to clipboard | No meaningful "open" action |
| FILE click behaviour | `Desktop.open` by extension | System handles format |
| EMAIL click behaviour | `mailto:` via `Desktop.mail` | Standard OS affordance |
| Description visibility | Tooltip only | Keeps list rows compact |
| Inbox exclusion | UI only (not model) | Past-clarification heuristic; easy to add later |
| Resource saves | Immediate on Add/Delete | No reason to defer; consistent with tag behaviour |

## Issues

| # | Title | Depends on |
|---|---|---|
| #275 | Domain model: Resource record, ResourceType enum, NamNode integration, persistence | — |
| #276 | ActionDialog: collapsible resources section | #275 |
| #277 | ProjectDialog: collapsible resources section | #275 |
| #278 | List views: paperclip indicator on resource-bearing rows | #275 |
| #279 | MCP server: list_resources, add_resource, remove_resource tools | #275 |

## Open questions

- None blocking. File browse button and resource reordering are acknowledged as future improvements.
