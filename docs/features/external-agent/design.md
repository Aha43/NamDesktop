# External Agent Integration: Monitoring Mode and MCP Server

> Feature design note — ready for implementation.
> Issue order: #249 first, then #250 and #251 independently.

## Why this direction

NamDesktop originally planned to embed AI via internal Anthropic API calls (#211–#214).
The direction changed: native AI tools (Claude with agency over email, calendar, web, etc.)
are fundamentally more capable than anything a Java HTTP client can replicate. The better
architecture is to make NamDesktop a **reactive display layer** that external agents write
into, rather than an AI client itself.

Both approaches can coexist. The `ai` issues (#211–#214) are deferred, not closed.
This `external` cluster is the priority.

## Core principle

**The AI stays in AI-land. NamDesktop stays in productivity-app-land.**
The contract between them is a JSON file on disk.

## Architecture

```
Claude conversation (with email, calendar, web tools)
    │  calls workspace tools via MCP
    ▼
NamMcpServer (same JAR, separate process)
    │  atomic writes to
    ▼
workspace.external.json
    │  file change detected by WatchService
    ▼
NamDesktop (running, monitoring mode ON)
    │  diffs + toasts + navigates
    ▼
User sees changes land in real time

On monitoring mode exit:
    summary dialog → Accept → workspace.external.json becomes workspace.json
                   → Reject → external discarded, workspace.json unchanged
```

## The file contract

| File | Owner | Purpose |
|---|---|---|
| `workspace.json` | NamDesktop | Live working workspace. Never touched by external agents. |
| `workspace.external.json` | MCP server | Created on monitoring mode entry (copy of current state). External agents write here only. Deleted on exit. |
| `.namdesktop-monitoring` | NamDesktop | Sentinel file. Written on mode entry, deleted on exit. MCP server reads this to know if writes are safe. |

## Monitoring mode UX

**Toggle:** toolbar button + `Cmd+Shift+M`. Status shown in title bar / status bar.

**On enter:** snapshot `workspace.json` → `workspace.external.json`, write sentinel.

**While active:** `WatchService` watches `workspace.external.json`.
- New inbox items → toast *"X item(s) added to Inbox"* + auto-navigate to Inbox.
- Status / structural changes → toast *"Workspace updated"* only.
- Unparseable file → log silently, keep last known good state, wait for next event.

**On exit:** diff external vs main → summary dialog (*"3 items added, 1 project created,
2 status changes"*) → **Accept** or **Reject**. No cherry-picking for now.

## MCP server tools

**Read tools** (always available):
- `get_workspace_context()` — compact projects + tags summary
- `list_inbox()` — inbox items with id, title, tags, status
- `list_projects()` — projects with id, title, status, child count
- `get_monitoring_status()` — reads sentinel file, returns mode state

**Write tools** (require monitoring mode):
- `add_inbox_item(title, description?, tags?)`
- `mark_next(node_id)`
- `mark_done(node_id)`
- `mark_backlog(node_id)`

If monitoring mode is off when a write tool is called: return a warning instructing the
user to enable monitoring mode (`Cmd+Shift+M`). Write nothing.

**Atomic write pattern:** read → modify in memory → write to `workspace.external.tmp`
→ atomic rename to `workspace.external.json`. NamDesktop never sees a partial file.

## User setup

```json
{
  "mcpServers": {
    "namdesktop": {
      "command": "java",
      "args": ["-cp", "/path/to/namdesktop.jar", "namdesktop.mcp.NamMcpServer",
               "--workspace", "/path/to/workspace.json"]
    }
  }
}
```

No new runtime. Users already have Java.

## Issues

| # | Scope | Depends on |
|---|---|---|
| [#249](https://github.com/Aha43/NamDesktop/issues/249) | Monitoring mode enter/exit, external file lifecycle, exit summary | — (do first) |
| [#250](https://github.com/Aha43/NamDesktop/issues/250) | Live change detection, toast reactions, Inbox auto-navigate | #249 |
| [#251](https://github.com/Aha43/NamDesktop/issues/251) | MCP server, workspace tools, atomic writes, mode-awareness | #249 |

#250 and #251 are independent of each other once #249 is done.

## Decisions settled

- **Generic external file name** (`workspace.external.json`) — not AI-specific, anything external can use it.
- **Atomic writes** on MCP side (temp + rename) + graceful parse on NamDesktop side = two-layer safety.
- **Inform + accept-all** on exit summary — no cherry-picking for now. Can add later with checkboxes.
- **MCP server in same JAR** — zero extra dependency for users, shared domain model.
- **Write tools warn, not block** — if monitoring mode is off, MCP tools explain and ask user to enable rather than silently failing.

## Future scope (not in these issues)

- Cherry-pick individual changes in exit summary
- `request_monitoring_mode` — MCP triggers NamDesktop to prompt user
- Additional write tools: node deletion, project creation, structural moves
- Internal AI (#211–#214) — still viable as a complement
