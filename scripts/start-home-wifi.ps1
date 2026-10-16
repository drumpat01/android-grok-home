param([switch]$HomeOnly)
. (Join-Path $PSScriptRoot 'common.ps1')
$ErrorActionPreference='Stop'
$network=Get-GrokNetwork
$computerAddress=$network.Address
$port=8788
$project=Split-Path $PSScriptRoot -Parent
$python=Get-GrokPython
& wsl.exe -d Ubuntu -- true
$bdkReady=$false
$toolList=$null
try{$toolList=Invoke-RestMethod 'http://127.0.0.1:8790/v1/tools' -TimeoutSec 2 -NoProxy;$bdkReady=@($toolList.tools.name) -contains 'native_activity';if(!$bdkReady){Write-Warning 'Restart the existing Cursor bridge to load native_activity.'}}catch{}
if(!$bdkReady){
    if(!$toolList){Start-Process -FilePath wsl.exe -WindowStyle Hidden -ArgumentList '-d','Ubuntu','--','bash',"$(Get-GrokLinuxProject $project)/scripts/start-bot-bridge.sh" -RedirectStandardOutput "$env:TEMP/grok-home-bots.log" -RedirectStandardError "$env:TEMP/grok-home-bots-error.log"}
    for($attempt=0;$attempt -lt 20;$attempt++){
        try{$toolList=Invoke-RestMethod 'http://127.0.0.1:8790/v1/tools' -TimeoutSec 2 -NoProxy;$bdkReady=@($toolList.tools.name) -contains 'native_activity';if($bdkReady){break}}catch{};Start-Sleep -Milliseconds 500
    }
}
if(!$bdkReady){Write-Warning 'Cursor bots are unavailable; see grok-home-bots-error.log in Temp.'}
if (!(Get-NetIPAddress -AddressFamily IPv4 -IPAddress $computerAddress -ErrorAction SilentlyContinue)) {
    throw "This computer's IP changed. Update the tablet's Voice connection and this script to the current Ethernet IPv4 address."
}
$health=$null
try { $health=Invoke-RestMethod "http://${computerAddress}:$port/health" -TimeoutSec 2 -NoProxy } catch {}
if($health -and $health.service -ne 'grok-home-voice'){throw "Port $port is used by another service."}
if($health -and (!$health.bots_enabled -or $health.xai_enabled -ne $false -or !$health.activity_enabled -or !$health.updates_enabled)){
    $listener=Get-NetTCPConnection -LocalAddress $computerAddress -LocalPort $port -State Listen -ErrorAction Stop | Select-Object -First 1
    $owner=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $expectedServer=(Join-Path $project 'backend/server.py').Replace('\','/')
    if($owner.ExecutablePath -ne $python -or !$owner.CommandLine.Replace('\','/').Contains($expectedServer)){throw 'Existing broker process identity did not match; restart it manually.'}
    Stop-Process -Id $listener.OwningProcess
    $health=$null
}
if(!$health){
    # Start WSL so its project directory and .env file are available through UNC.
    & wsl.exe -d Ubuntu -- true
    if($LASTEXITCODE -ne 0){throw 'Ubuntu WSL did not start.'}
    $server=Join-Path $project 'backend/server.py'
    Start-Process -FilePath $python -WorkingDirectory $env:USERPROFILE -WindowStyle Hidden -ArgumentList $server,'--bind',$computerAddress,'--port',"$port",'--lan-cidr',$network.Subnet,'--enable-bots','--disable-xai' -RedirectStandardOutput "$env:TEMP/grok-home-wifi.log" -RedirectStandardError "$env:TEMP/grok-home-wifi-error.log"
    for($attempt=0;$attempt -lt 15;$attempt++){
        try{$health=Invoke-RestMethod "http://${computerAddress}:$port/health" -TimeoutSec 2 -NoProxy;break}catch{Start-Sleep -Milliseconds 500}
    }
}
if(!$health -or $health.service -ne 'grok-home-voice'){throw 'Home Wi-Fi server failed to start; see grok-home-wifi-error.log in Temp.'}
Write-Host "Home Wi-Fi voice server: http://${computerAddress}:$port"
Write-Host 'Keep this computer on and awake. The tablet can now use home Wi-Fi without USB.'
if($env:GROK_HOME_TABLET_SERIAL){& "$PSScriptRoot/start-tablet-reconnect.ps1"}


if(!$HomeOnly){try{& "$PSScriptRoot/start-tailscale.ps1" -BridgeReady}catch{Write-Warning "Private away connection: $($_.Exception.Message)"}}
