$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar        = Join-Path $scriptDir '..\build\app\NamDesktop.jar'
$lib        = Join-Path $scriptDir '..\build\app\lib\*'
$workspace  = if ($env:NAMDESKTOP_WORKSPACE) { $env:NAMDESKTOP_WORKSPACE }
              else { Join-Path $HOME '.namdesktop\workspace.json' }

$cp = "$jar$([System.IO.Path]::PathSeparator)$lib"
& java -cp $cp namdesktop.mcp.NamMcpServer --workspace $workspace @args
