$ErrorActionPreference = "Stop"

$FlatLafVersion  = "3.4.1"
$JacksonVersion  = "2.15.2"
$JsvgVersion     = "1.7.2"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir   = Resolve-Path (Join-Path $ScriptDir "..")
$LibDir    = Join-Path $RootDir "lib"

New-Item -ItemType Directory -Force $LibDir | Out-Null

$Maven = "https://repo1.maven.org/maven2"

$Dependencies = @(
    @{
        File      = "flatlaf-$FlatLafVersion.jar"
        GroupPath = "com/formdev/flatlaf/$FlatLafVersion"
    },
    @{
        File      = "flatlaf-extras-$FlatLafVersion.jar"
        GroupPath = "com/formdev/flatlaf-extras/$FlatLafVersion"
    },
    @{
        File      = "jackson-annotations-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/core/jackson-annotations/$JacksonVersion"
    },
    @{
        File      = "jackson-core-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/core/jackson-core/$JacksonVersion"
    },
    @{
        File      = "jackson-databind-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/core/jackson-databind/$JacksonVersion"
    },
    @{
        File      = "jsvg-$JsvgVersion.jar"
        GroupPath = "com/github/weisj/jsvg/$JsvgVersion"
    }
)

foreach ($Dep in $Dependencies) {
    $TargetPath = Join-Path $LibDir $Dep.File

    if (Test-Path $TargetPath) {
        Write-Host "Already exists: $($Dep.File)"
        continue
    }

    $Url = "$Maven/$($Dep.GroupPath)/$($Dep.File)"
    Write-Host "Downloading $($Dep.File)..."
    Invoke-WebRequest -Uri $Url -OutFile $TargetPath
}

Write-Host "Done. Libraries in:"
Write-Host "  $LibDir"