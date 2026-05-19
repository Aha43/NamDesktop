$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir   = Resolve-Path (Join-Path $ScriptDir "..")

. (Join-Path $RootDir "packaging/packaging-common.ps1")

Set-Location $RootDir

$ReleaseVersion   = Get-ProjectVersion -RootDir $RootDir
$TagName          = "v$ReleaseVersion"
$ReleaseTitle     = "NamDesktop $TagName"
$PackageDir       = "dist/package"
$ReleaseNotesFile = "dist/release-notes-$TagName.md"

$MacArtifact     = Join-Path $PackageDir "NamDesktop-$TagName-macos-x64.dmg"
$WindowsArtifact = Join-Path $PackageDir "NamDesktop-$TagName-windows-x64.exe"

Write-Host "Checking GitHub CLI..."
gh --version | Out-Null

Write-Host "Checking git status..."
$Status = git status --porcelain
if ($Status) {
    throw "Working tree is not clean. Commit or stash changes before creating release."
}

Write-Host "Ensuring tag: $TagName"
$LocalTag = git tag --list $TagName
if (-not $LocalTag) {
    Write-Host "Creating local tag $TagName..."
    git tag -a $TagName -m $ReleaseTitle
}

Write-Host "Pushing tag..."
git push origin $TagName

New-Item -ItemType Directory -Force "dist" | Out-Null

Write-Host "Writing release notes: $ReleaseNotesFile"
@"
## NamDesktop $TagName

### Changes

See [CHANGELOG.md](CHANGELOG.md) for details.

### Downloads

- macOS x64 DMG (if attached)
- Windows x64 installer (if attached)

### Notes

Builds are unsigned. macOS or Windows may warn before opening — this is expected.
"@ | Set-Content -Path $ReleaseNotesFile -Encoding UTF8

Write-Host "Checking for existing GitHub release..."
gh release view $TagName *> $null
$ReleaseExists = ($LASTEXITCODE -eq 0)

if (-not $ReleaseExists) {
    Write-Host "Creating draft release: $TagName"
    gh release create $TagName `
        --title $ReleaseTitle `
        --notes-file $ReleaseNotesFile `
        --draft
    if ($LASTEXITCODE -ne 0) { throw "Failed to create release: $TagName" }
} else {
    Write-Host "Release already exists — updating."
    gh release edit $TagName `
        --title $ReleaseTitle `
        --notes-file $ReleaseNotesFile `
        --draft=true
    if ($LASTEXITCODE -ne 0) { throw "Failed to update release: $TagName" }
}

$Artifacts = @()
if (Test-Path $MacArtifact)     { $Artifacts += $MacArtifact }
if (Test-Path $WindowsArtifact) { $Artifacts += $WindowsArtifact }

if ($Artifacts.Count -eq 0) {
    Write-Host "No artifacts found in $PackageDir — draft created without uploads."
    exit 0
}

Write-Host "Uploading artifacts..."
foreach ($Artifact in $Artifacts) {
    Write-Host "  $Artifact"
    gh release upload $TagName $Artifact --clobber
    if ($LASTEXITCODE -ne 0) { throw "Failed to upload: $Artifact" }
}

Write-Host "Done. Draft release: $TagName"
Write-Host "Publish when all platform artifacts are uploaded:"
Write-Host "  gh release edit $TagName --draft=false"