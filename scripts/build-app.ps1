$ErrorActionPreference = "Stop"

$AppName   = "NamDesktop"
$MainClass = "namdesktop.app.NamDesktopMain"
$MainJar   = "NamDesktop.jar"

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir    = Resolve-Path (Join-Path $ScriptDir "..")

$SrcDir     = Join-Path $RootDir "src"
$LibDir     = Join-Path $RootDir "lib"
$BuildDir   = Join-Path $RootDir "build"
$ClassesDir = Join-Path $BuildDir "classes"
$AppDir     = Join-Path $BuildDir "app"
$AppLibDir  = Join-Path $AppDir "lib"

Set-Location $RootDir

Write-Host "Cleaning build..."
Remove-Item -Recurse -Force $BuildDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $ClassesDir | Out-Null
New-Item -ItemType Directory -Force $AppLibDir  | Out-Null

Write-Host "Compiling Java sources..."
$Sources = Get-ChildItem -Path $SrcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

if ($Sources.Count -eq 0) {
    throw "No Java source files found under $SrcDir"
}

javac `
    -cp "$LibDir/*" `
    -d $ClassesDir `
    $Sources

if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)" }

Write-Host "Creating JAR..."
jar `
    --create `
    --file (Join-Path $AppDir $MainJar) `
    --main-class $MainClass `
    -C $ClassesDir . `
    -C $RootDir VERSION

if ($LASTEXITCODE -ne 0) { throw "jar failed (exit $LASTEXITCODE)" }

Write-Host "Copying dependencies..."
Copy-Item -Path (Join-Path $LibDir "*.jar") -Destination $AppLibDir

Write-Host "Done. Application layout:"
Write-Host "  $AppDir"