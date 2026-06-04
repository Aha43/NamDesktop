$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar       = Join-Path $scriptDir '..\build\app\NamDesktop.jar'
$lib       = Join-Path $scriptDir '..\build\app\lib\*'
$workspace = Join-Path $HOME '.namdesktop\dev\workspace.json'

$cp = "$jar$([System.IO.Path]::PathSeparator)$lib"
& java -cp $cp namdesktop.mcp.NamMcpServer --workspace $workspace --direct
