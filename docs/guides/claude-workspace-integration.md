# Setting Up NamDesktop So Claude Can Update Your Workspace

This guide walks through connecting NamDesktop to Claude Code so that Claude can read your workspace and make live changes — adding inbox items, marking actions done, creating projects — while you watch them appear in real time.

## How it works

NamDesktop exposes an MCP (Model Context Protocol) server that Claude can call as tools. When you enable **monitoring mode** in NamDesktop, the app watches a staging file (`workspace.external.json`) for changes. Claude writes to that file through the MCP server; NamDesktop detects each write within half a second and updates the UI live. When you exit monitoring mode you review a summary of all changes and choose to accept or reject them.

```
Claude (with MCP tools)
    │  calls workspace tools
    ▼
NamMcpServer (same JAR, separate process)
    │  atomic writes to
    ▼
workspace.external.json
    │  detected within ~500ms by
    ▼
NamDesktop (monitoring mode ON)
    │  live panel updates + toast
    ▼
Exit monitoring mode → Accept or Reject
```

## Prerequisites

- NamDesktop built (`make` run at least once — JAR at `build/app/NamDesktop.jar`)
- Claude Code CLI installed and authenticated
- PowerShell (`pwsh`) available (used by the launch script)

## Step 1 — Build the JAR

```bash
make
```

The JAR and its dependencies land in `build/app/`. You only need to rebuild when you update NamDesktop.

## Step 2 — Register the MCP server with Claude Code

Run this once in a terminal. It registers the server globally so it is available in every Claude Code session.

**Production workspace** (your real data):
```bash
claude mcp add --scope user namdesktop pwsh /Users/<you>/dev/repos/NamDesktop/scripts/mcp-server.ps1
```

**Dev workspace** (safe for testing — matches NamDesktop's dev mode):
```bash
claude mcp add --scope user namdesktop-dev \
  --env NAMDESKTOP_WORKSPACE=/Users/<you>/.namdesktop/dev/workspace.json \
  pwsh /Users/<you>/dev/repos/NamDesktop/scripts/mcp-server.ps1
```

Verify:
```bash
claude mcp list
# Should show: namdesktop, namdesktop-dev
```

## Step 3 — Start a session

Every time you want Claude to work with your workspace:

1. **Launch NamDesktop.** The app must be running for live updates to appear.
   - Normal mode: double-click the app or run `java -cp build/app/NamDesktop.jar:build/app/lib/* namdesktop.app.NamDesktopMain`
   - Dev mode: launch and select dev mode at the splash screen

2. **Enable monitoring mode.** Press `Cmd+Shift+M` or click the antenna button (⌗) in the toolbar. The title bar shows `[Monitoring]` when active.

3. **Open a new Claude Code session** (MCP servers load at startup).

4. **Orient Claude** with an opening prompt:

   > "Check my NamDesktop monitoring status and give me a summary of my workspace."

   This confirms the connection and gives Claude context before any work begins. If using dev mode, add: *"Use namdesktop-dev tools today."*

## Step 4 — Work with Claude

Claude can now read and write your workspace. Examples:

| What you say | Tool called |
|---|---|
| "What's in my workspace?" | `get_workspace_context` |
| "Show me my inbox" | `list_inbox` |
| "Add 'Buy oat milk' to my inbox" | `add_inbox_item` |
| "Mark that action as done" | `mark_done` |
| "Add 'Email supplier' as a next action" | `add_next_action` |

Changes appear live in NamDesktop as Claude makes them — inbox items land in Inbox, actions appear in the project workbench, status changes are reflected immediately.

## Step 5 — Exit monitoring mode

When you are done, press `Cmd+Shift+M` again (or click the antenna button).

- If Claude made changes, a summary dialog appears: *"3 items added to Inbox, 1 action added, 2 status changes"*
- **Accept** — changes are written permanently to your workspace
- **Reject** — all changes are discarded, workspace restored to its state before monitoring mode

If you reject, NamDesktop reverts to the original data immediately.

## Tips

- **Read tools always work** — `get_workspace_context`, `list_inbox`, `list_projects`, and `get_monitoring_status` work without monitoring mode. Only writes require it.
- **Test in dev mode first.** Dev mode uses a separate workspace file so you can experiment freely without touching your real data.
- **Rebuild after updates.** If you pull new NamDesktop code, run `make` again before starting a session — Claude Code always starts the MCP server fresh from the JAR.
- **If Claude says monitoring mode is off** — you forgot step 2. Press `Cmd+Shift+M` in NamDesktop and ask Claude to retry.
- **If no live update appears** — the MCP server may be pointing at the wrong workspace path (e.g. dev vs. prod). Check with `get_monitoring_status`.
