# Changelog

All notable changes to NamDesktop will be documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

- Initial project scaffold.
- `NamNode` and `NamWorkspace` domain model with `NodeStatus` enum (`namdesktop.model` package).
- `WorkspaceRepository` interface and `JsonWorkspaceRepository` implementation for JSON persistence (`namdesktop.persist` package).
- `MainFrame` with horizontal split-pane layout (left panel + centre work area).
- `WorkspaceTreeModel` and `TreePanel` display the node tree in the left panel.
- Workspace loaded from `~/.namdesktop/workspace.json` on startup; falls back to a default workspace if the file is absent.
- `NamWorkspaceService` command layer for all workspace mutations (`addChild`, `renameNode`, `deleteLeaf`, `markDone`).