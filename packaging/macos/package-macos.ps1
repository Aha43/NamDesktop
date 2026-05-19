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

Write-Host "Building app..."
make clean
make app

Write-Host "Cleaning package output..."
Remove-Item -Recurse -Force "dist/package" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "dist/package" | Out-Null

Write-Host "Creating macOS DMG..."
jpackage `
    --type dmg `
    --name $AppName `
    --app-version $AppVersion `
    --input "build/app" `
    --main-jar $MainJar `
    --main-class $MainClass `
    --dest "dist/package"

$GeneratedDmg = "dist/package/$AppName-$AppVersion.dmg"
$ReleaseDmg   = "dist/package/$AppName-v$ReleaseVersion-macos-$Arch.dmg"

if (-not (Test-Path $GeneratedDmg)) {
    throw "jpackage did not create expected DMG: $GeneratedDmg"
}

Move-Item -Force $GeneratedDmg $ReleaseDmg

Write-Host "Done. Package:"
Write-Host "  $ReleaseDmg"