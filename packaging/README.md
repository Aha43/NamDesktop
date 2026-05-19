# Packaging NamDesktop

Native installers are produced with `jpackage` (bundled with JDK 17+).

## Prerequisites

- JDK 17+ on the machine that builds the package.
- On Windows: WiX Toolset 3.x for EXE installer support.

## macOS DMG (run on a Mac)

```powershell
pwsh packaging/macos/package-macos.ps1
```

Output: `dist/package/NamDesktop-v<version>-macos-x64.dmg`

## Windows EXE installer (run on Windows)

```powershell
pwsh packaging/windows/package-windows.ps1
```

Output: `dist/package/NamDesktop-v<version>-windows-x64.exe`

## GitHub release

After building one or both platform packages, create a draft GitHub release and
upload the artifacts:

```powershell
pwsh packaging/create-release.ps1
```

Then publish the release manually on GitHub once all artifacts are attached.

## Release process

1. Update `VERSION` file (e.g. `0.2.0`).
2. Update `## [Unreleased]` section in `CHANGELOG.md` → rename to `## [0.2.0]`.
3. Commit: `git commit -m "chore: release 0.2.0"`.
4. Build platform packages (on each target OS).
5. Run `pwsh packaging/create-release.ps1`.
6. Publish draft on GitHub.