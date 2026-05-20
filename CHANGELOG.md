# Changelog

All notable changes to NamDesktop will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

- JUnit 5 (`junit-platform-console-standalone-1.10.2`) wired into the project; `make test` compiles and runs the test suite.
- Navigation shell: left nav list (`NavigationEntry`, `NavigationPanel`) and swappable central content area (`ContentArea`) replace the raw split-pane layout.
- Raw tree demoted to a selectable "Raw Tree" navigation view; app opens on "Inbox" by default.
- Unit tests for `NamWorkspace` (8 tests covering `createDefault`, `getNode`, and `getChildren`).
- Unit tests for `NamWorkspaceService` (16 tests covering all commands, save behaviour, and error cases; in-memory repository stub — no filesystem I/O).

### Added

- Initial project scaffold.
- `NamNode` and `NamWorkspace` domain model with `NodeStatus` enum (`namdesktop.model` package).
- `WorkspaceRepository` interface and `JsonWorkspaceRepository` implementation for JSON persistence (`namdesktop.persist` package).
- `MainFrame` with horizontal split-pane layout (left panel + centre work area).
- `WorkspaceTreeModel` and `TreePanel` display the node tree in the left panel.
- Workspace loaded from `~/.namdesktop/workspace.json` on startup; falls back to a default workspace if the file is absent.
- `NamWorkspaceService` command layer for all workspace mutations (`addChild`, `renameNode`, `deleteLeaf`, `markDone`).
- Right-click context menu on the node tree for adding, renaming, and deleting nodes.
- "Mark done" context menu action; done nodes render with strikethrough and grey text.