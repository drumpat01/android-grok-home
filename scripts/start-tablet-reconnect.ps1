$ErrorActionPreference='Stop'
$watcher=Join-Path $PSScriptRoot 'connect-tablet.ps1'
$pattern='-File\s+"?'+[regex]::Escape($watcher)+'"?\s+-Watch(?:\s|$)'
$running=Get-CimInstance Win32_Process | Where-Object {$_.CommandLine -and $_.CommandLine -match $pattern}
if(!$running){
    $pwsh=(Get-Process -Id $PID).Path
    Start-Process -FilePath $pwsh -WindowStyle Hidden -WorkingDirectory $env:USERPROFILE -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',$watcher,'-Watch' -RedirectStandardOutput "$env:TEMP/grok-tablet-reconnect.log" -RedirectStandardError "$env:TEMP/grok-tablet-reconnect-error.log"
}
