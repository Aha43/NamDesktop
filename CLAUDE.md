# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Always present a plan and wait for explicit approval before editing any files or running commands.

## Build commands

```bash
make          # compile, package JAR, copy deps -> build/app/
make run      # build then launch the app
make clean    # delete build/
```

To download dependencies before the first build:

```powershell
pwsh scripts/download-libs.ps1
```

To download SVG icons (Tabler Icons, MIT):

```bash
bash scripts/download-icons.sh
```

Icons live in `src/icons/` and are copied to `build/classes/icons/` automatically by `make`.
Load them with `new FlatSVGIcon("/icons/name.svg")`. The icons use `stroke="currentColor"` (pitch black); `UiTheme` installs a global `FlatSVGIcon.ColorFilter` that recolors pure black to a light gray in dark themes (and near-black in light), so they stay legible — always go through `UiTheme.apply(...)` to set up the look-and-feel.
To add a new icon, add its name to the `ICONS` array in `scripts/download-icons.sh` and re-run it.

## Architecture

NamDesktop is a Java Swing desktop application built without a build tool (no Maven/Gradle).
Source lives under `src/namdesktop/`, dependencies under `lib/`, build output under `build/`.

### Entry point

`namdesktop.app.NamDesktopMain` — wires up Look & Feel, creates the main `JFrame`, and shows it on the EDT.

### Key packages

| Package | Responsibility |
|---|---|
| `namdesktop.app` | Entry point, `AppInfo` (name + version) |
| `namdesktop.ui` | Main frame and top-level Swing panels |

### Dependencies (`lib/`)

- **FlatLaf** — cross-platform Swing look-and-feel (light/dark themes)
- **Jackson** — JSON serialisation (add when you need persistence)
- **JSVG** — SVG icon support (add when you need vector icons)

Run `pwsh scripts/download-libs.ps1` to download all of the above from Maven Central.

## Workflow

- **Always work on a GitHub issue.** Never start implementation without a corresponding issue —
  either one created upfront or one we create together before coding begins.
  Include `Closes #<number>` in every non-chore commit.
- **Always check the current branch before committing.** If on `main`, warn and stop.
  All feature work must go on a feature branch.
- **Default feature branch name is `feature/next`.** Rename it to something descriptive
  (e.g. `feature/dark-mode`) before opening a PR.
- When completing a GitHub issue, update the `## [Unreleased]` section of `CHANGELOG.md`
  before committing. Use `Added`, `Changed`, or `Fixed` as appropriate, and include
  `Closes #<number>` in the commit message.
- **Always run `make run` after every change** — even trivial ones — so the user can test
  before committing. Never skip this step.
- **Always run `make test` before committing** to confirm existing tests still pass.
- **One issue at a time.** After completing an issue, stop and wait for the user to confirm
  before starting the next one — even when multiple issues are planned for the same sprint.
  If a change is purely internal (model/service with full unit test coverage and no UI),
  say so explicitly and still wait for a go-ahead before moving on.

### Definition of Done for feature issues

A feature issue is complete when:
- the feature works (`make run` verified)
- relevant unit tests are added or updated
- all existing tests pass (`make test`)
- no obvious domain invariant is weakened

### Sprint-end checklist (before opening a PR)

Before merging any sprint branch, ask:

1. **e2e** — New workspace mutations? Add steps to `src/e2e.json`; wire new action/assert handlers in `NamDemoWiring` / `NamAssertWiring` if needed.
2. **MCP** — New fields or operations an AI agent should see? Update `NamMcpServer`: expose new fields in list tools, accept new params in write tools, or add new tools.
3. **Help** — New panel or nav entry? Write a new help article and add to `HelpPanel` sidebar. New columns or controls on an existing panel? Update that panel's article. New concept? Link it from Getting Started "What's next?".

Use a dedicated test sprint when:
- a feature touches core model behaviour
- the implementation changed more than expected
- new invariants appeared during development
- edge cases are discovered after review
- confidence feels lower than the code size suggests

### Testing preference

Prefer small, focused unit tests around domain model, command layer, persistence, and ordering rules before UI tests. Swing UI tests can wait unless behaviour cannot be verified elsewhere.

## Conventions

- All Swing work must happen on the Event Dispatch Thread (`SwingUtilities.invokeLater`).
- Prefer `var` for local variables where the type is obvious from context.
- No Maven/Gradle — keep the build simple and direct.