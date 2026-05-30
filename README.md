<div align="center">
  <img src="src/icons/logo-wordmark.svg" alt="NamDesktop" height="64">
  <br/>
  <em>A local-first, GTD-inspired personal productivity desktop app built with Java and Swing.</em>
</div>

---

## What is NamDesktop?

NamDesktop is a desktop application for managing your work through a GTD-inspired system. All data lives in a single node tree stored locally as JSON. There is no cloud account, no subscription, and no mandatory sync — your data is a file on your machine.

The same underlying nodes are interpreted differently depending on where they sit in the tree, their status, their tags, and which lens (view) is active. The goal is a small, fast, focused tool that stays out of your way.

## Features

**Capture and process**
- **Inbox** — capture anything quickly; process each item into an action, a project, or delete it
- **Bulk entry** — paste multiple lines to create several actions at once

**Organise**
- **Projects** — nodes with children; nest sub-projects to any depth
- **Project Workbench** — action-forward view of a project and its sub-projects; inline rename, status toggle, drag-free reorder
- **Next Actions** — nodes with status `NEXT`; what you can actually do right now
- **Backlog** — nodes with status `BACKLOG`; parked for later without losing them
- **Done** — completed actions kept for reference

**Focus**
- **Context / Tags** — filter next actions by tags (`@home`, `@computer`, …)
- **Saved Views** — save a tag filter as a named view; toggle Next-only mode
- **Mission Control** — high-level readiness dashboard across a set of tag-filtered projects
- **Search** — full-text search across all nodes

**Dependencies**
- **Blocked by** — mark an action as blocked by one or more other actions; the Done button is disabled until all blockers are complete; completing a blocker shows a nudge listing what it unblocked

**Resources**
- Attach typed resources to any action or project: `URI`, `EMAIL`, `FILE`, or `TEXT`
- Clicking a resource opens it (browser / mail client / Finder) or copies it to the clipboard
- A paperclip indicator appears in all list views when a node has attachments

**Templates**
- Save a project's child structure as a named template
- Apply a template to any project to clone the structure instantly

**Claude / MCP integration**
- Built-in MCP stdio server — wire it to Claude Desktop and manage your workspace through natural language
- **Monitoring mode** — NamDesktop watches `workspace.external.json` for changes written by Claude; accept or reject each batch with a summary diff; Checkpoint flushes accepted changes without leaving monitoring
- MCP tools: list inbox, projects, next actions, done, saved views; find nodes; create projects and actions; set status; add/remove resources; check monitoring status

**Timestamps** *(data only — UI coming soon)*
- `createdAt`, `updatedAt`, `statusChangedAt` on every node; opening a dialog counts as a "seen" touch
- Foundation for staleness detection and FIFO/LIFO sorting

**Other**
- Dark and light themes (FlatLaf)
- Optional Git-backed sync — push/pull the workspace JSON to a remote repository
- Dev mode — separate workspace for testing without touching production data
- Keyboard shortcuts (`Cmd+1–5` for panels, `Cmd+F` search, `Cmd+Shift+M` monitoring, `Cmd+/` shortcuts reference)

## Development model

NamDesktop is developed in small, issue-driven increments with AI assistance: ChatGPT handles high-level design and conceptual discussion; Claude Code handles concrete implementation. Every change starts from a GitHub issue. The aim is disciplined AI-assisted development — not uncontrolled vibe coding.

The project is also a quiet experiment: can a GTD app eventually manage its own backlog? See [IDEAS.md](docs/IDEAS.md) for the running list of future directions.

---

## Prerequisites

- Java 21+ (JDK, not just JRE)
- GNU Make (macOS/Linux) or `make` via winget/scoop (Windows)
- PowerShell 5.1+ (already present on Windows; `pwsh` on macOS/Linux)

## Getting started

```bash
# 1. Download dependencies into lib/
pwsh scripts/download-libs.ps1

# 2. Build and run
make run
```

## Build commands

```bash
make          # compile, package JAR, copy deps -> build/app/
make run      # build then launch the app
make test     # run the test suite
make clean    # delete build/
```

## MCP server setup

Add to `~/.claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "namdesktop": {
      "command": "java",
      "args": [
        "-cp", "/path/to/NamDesktop.jar:lib/*",
        "namdesktop.mcp.NamMcpServer",
        "--workspace", "/Users/<you>/.namdesktop/workspace.json"
      ]
    }
  }
}
```

Then enable monitoring mode in the app (`Cmd+Shift+M`) before asking Claude to make changes.

## Packaging (native installers)

See [packaging/README.md](packaging/README.md). To generate the macOS `.icns` from the logo:

```bash
bash scripts/make-icns.sh
```
