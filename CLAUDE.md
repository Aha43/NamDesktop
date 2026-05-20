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

No test framework is wired in yet. Compile success (`make`) is the main verification step.

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