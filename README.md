# NamDesktop

> A local-first, GTD-inspired personal productivity desktop app built with Java and Swing.

## What is NamDesktop?

NamDesktop is a desktop application for managing your work through a GTD-inspired system. All data lives in a single persistent node tree stored locally as JSON. An optional Git-backed sync lets you push and pull the workspace to a remote repository, but there is no mandatory cloud account or subscription. The same underlying nodes are interpreted differently depending on where they sit in the tree, their status, their tags, and which lens (view) is active. The goal is a small, fast, focused tool that stays out of your way.

## Core concepts

- **Inbox** — capture anything quickly; process later into an action or a project
- **Projects** — nodes with children; the Project Workbench gives an action-forward view of a project and its sub-projects without opening dialogs
- **Next Actions** — nodes with status `NEXT`; what you can actually do right now
- **Backlog** — nodes with status `BACKLOG`; things parked for later without losing them
- **Context** — filter next actions by tags (`@home`, `@computer`, …); save a filter as a named view
- **Lenses** — views over the same node tree; the architecture allows new lenses to emerge without restructuring the data model
- **Templates** — save a project's structure as a reusable template and apply it when creating new projects

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
make run          # macOS / Linux
make -f makefile.windows run   # Windows
```

## Build commands

```bash
make          # compile, package JAR, copy deps -> build/app/
make run      # build then launch the app
make clean    # delete build/
```

## Packaging (native installers)

See [packaging/README.md](packaging/README.md).
