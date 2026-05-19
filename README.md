# NamDesktop

> _One-line description of what this app does._

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