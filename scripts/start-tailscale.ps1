param([switch]$BridgeReady)
. (Join-Path $PSScriptRoot 'common.ps1')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$tailscale='C:/Program Files/Tailscale/tailscale.exe'
$python=Get-GrokPython
$port=8792
$httpsPort=9443
if(!(Test-Path $tailscale)){throw 'Install and sign into Tailscale on this computer.'}
$state=& $tailscale status --json | ConvertFrom-Json
if($LASTEXITCODE -ne 0 -or $state.BackendState -ne 'Running'){throw 'Connect Tailscale on this computer first.'}
$proxyName=$state.Self.DNSName.TrimEnd('.')
if($proxyName -notmatch '^[a-z0-9.-]+\.ts\.net$'){throw 'Tailscale did not return a private DNS hostname.'}
if(!$BridgeReady){& "$PSScriptRoot/start-home-wifi.ps1" -HomeOnly}
$health=$null
try{$health=Invoke-RestMethod "http://127.0.0.1:$port/health" -TimeoutSec 2 -NoProxy}catch{}
if($health -and ($health.service -ne 'grok-home-voice' -or !$health.bots_enabled -or $health.xai_enabled -ne $false)){throw "Port $port is already used by another service or configuration."}
if($health -and (!$health.activity_enabled -or !$health.updates_enabled)){
    $listener=Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort $port -State Listen -ErrorAction Stop | Select-Object -First 1
    $owner=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $expectedServer=(Join-Path $project 'backend/server.py').Replace('\','/')
    if($owner.ExecutablePath.Replace('\','/') -ne $python -or !$owner.CommandLine.Replace('\','/').Contains($expectedServer)){throw 'Existing private broker identity did not match.'}
    Stop-Process -Id $listener.OwningProcess;$health=$null
}
if(!$health){
    $server=Join-Path $project 'backend/server.py'
    Start-Process -FilePath $python -WorkingDirectory $env:USERPROFILE -WindowStyle Hidden -ArgumentList $server,'--bind','127.0.0.1','--port',"$port",'--enable-bots','--disable-xai','--tailscale-host',$proxyName -RedirectStandardOutput "$env:TEMP/grok-home-tailscale.log" -RedirectStandardError "$env:TEMP/grok-home-tailscale-error.log"
    for($attempt=0;$attempt -lt 15;$attempt++){
        try{$health=Invoke-RestMethod "http://127.0.0.1:$port/health" -TimeoutSec 2 -NoProxy;break}catch{Start-Sleep -Milliseconds 500}
    }
}
if(!$health){throw 'Private bridge failed to start; see grok-home-tailscale-error.log in Temp.'}
$null=Invoke-RestMethod "http://127.0.0.1:$port/health" -Headers @{Host="${proxyName}:$httpsPort"} -TimeoutSec 3 -NoProxy
$config=& $tailscale serve status --json | ConvertFrom-Json
$existing=$config.Web.PSObject.Properties["${proxyName}:$httpsPort"]
$destination="http://127.0.0.1:$port"
if($existing -and $existing.Value.Handlers.'/'.Proxy -ne $destination){throw "Tailscale HTTPS port $httpsPort already belongs to another app."}
if(!$existing){
    & $tailscale serve --bg --https=$httpsPort $destination
    if($LASTEXITCODE -ne 0){throw 'Tailscale Serve configuration failed.'}
}
$url="https://${proxyName}:$httpsPort"
$remote=Invoke-RestMethod "$url/health" -TimeoutSec 20 -NoProxy
if($remote.service -ne 'grok-home-voice' -or !$remote.bots_enabled -or $remote.xai_enabled -ne $false){throw 'Private HTTPS health check failed.'}
Write-Host "Grok Home private connection: $url"
Write-Host 'Connect Tailscale on the tablet using the same account. Keep this computer awake.'
