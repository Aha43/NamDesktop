$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir   = Resolve-Path (Join-Path $ScriptDir "../..")

. (Join-Path $RootDir "packaging/packaging-common.ps1")

$AppName        = "NamDesktop"
$ReleaseVersion = Get-ProjectVersion -RootDir $RootDir
$AppVersion     = Get-NativeAppVersion -ReleaseVersion $ReleaseVersion
$Arch           = "x64"
$MainClass      = "namdesktop.app.NamDesktopMain"
$MainJar        = "NamDesktop.jar"

Set-Location $RootDir

Write-Host "Ensuring dependencies..."
pwsh -ExecutionPolicy Bypass -NoProfile -File .\scripts\download-libs.ps1
if ($LASTEXITCODE -ne 0) { throw "download-libs.ps1 failed (exit $LASTEXITCODE)" }

Write-Host "Building app..."
make -f makefile.windows clean
make -f makefile.windows app
if ($LASTEXITCODE -ne 0) { throw "make failed (exit $LASTEXITCODE)" }

Write-Host "Cleaning package output..."
Remove-Item -Recurse -Force "dist/package" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "dist/package" | Out-Null

Write-Host "Creating Windows installer..."
jpackage `
    --type exe `
    --name $AppName `
    --app-version $AppVersion `
    --input "build/app" `
    --main-jar $MainJar `
    --main-class $MainClass `
    --dest "dist/package" `
    --win-menu `
    --win-shortcut

$GeneratedExe = "dist/package/$AppName-$AppVersion.exe"
$ReleaseExe   = "dist/package/$AppName-v$ReleaseVersion-windows-$Arch.exe"

if (-not (Test-Path $GeneratedExe)) {
    throw "jpackage did not create expected installer: $GeneratedExe"
}

Move-Item -Force $GeneratedExe $ReleaseExe

Write-Host "Done. Package:"
Write-Host "  $ReleaseExe"