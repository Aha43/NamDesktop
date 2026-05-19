function Get-ProjectRoot {
    $ScriptDir = Split-Path -Parent $MyInvocation.PSCommandPath
    return Resolve-Path (Join-Path $ScriptDir "..")
}

function Get-ProjectVersion {
    param(
        [string]$RootDir = (Get-ProjectRoot)
    )
    $VersionFile = Join-Path $RootDir "VERSION"
    if (-not (Test-Path $VersionFile)) {
        throw "VERSION file not found: $VersionFile"
    }
    $Version = (Get-Content $VersionFile -Raw).Trim()
    if ($Version -notmatch '^\d+\.\d+\.\d+$') {
        throw "Invalid VERSION value: $Version"
    }
    return $Version
}

function Get-NativeAppVersion {
    param(
        [Parameter(Mandatory=$true)]
        [string]$ReleaseVersion
    )
    $Parts = $ReleaseVersion.Split(".")
    if ($Parts.Count -ne 3) {
        throw "Invalid release version: $ReleaseVersion"
    }
    # jpackage / macOS rejects native versions starting with 0.
    # 0.4.0 -> 1.4.0
    return "1.$($Parts[1]).$($Parts[2])"
}