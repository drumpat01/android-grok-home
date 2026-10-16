param([switch]$Once)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if($env:GROK_HOME_PROJECT){$project=$env:GROK_HOME_PROJECT}
$privateUrl=$env:GROK_HOME_PRIVATE_URL
if(!$privateUrl -or $privateUrl -notmatch '^https://[a-z0-9.-]+\.ts\.net(?::[0-9]+)?/?$'){throw 'Set GROK_HOME_PRIVATE_URL to your private Tailscale HTTPS URL.'}
$log=Join-Path $env:LOCALAPPDATA 'GrokHome/bridge-monitor.log'
$null=New-Item -ItemType Directory -Force (Split-Path $log)
$mutex=[Threading.Mutex]::new($false,'Local\GrokHomeBridgeMonitor')
if(!$mutex.WaitOne(0)){ $mutex.Dispose(); exit 0 }
function Write-MonitorLog([string]$Message){
    if((Test-Path $log) -and (Get-Item $log).Length -gt 1048576){Move-Item -LiteralPath $log -Destination "$log.old" -Force}
    Add-Content -LiteralPath $log -Value "$(Get-Date -Format o) $Message"
}
function Test-Bridge {
    try {
        $tools=Invoke-RestMethod 'http://127.0.0.1:8790/v1/tools' -TimeoutSec 5 -NoProxy
        $health=Invoke-RestMethod "$($privateUrl.TrimEnd('/'))/health" -TimeoutSec 10 -NoProxy
        return (@($tools.tools.name) -contains 'native_activity' -and $health.service -eq 'grok-home-voice' -and $health.bots_enabled -and $health.activity_enabled -and $health.updates_enabled -and $health.xai_enabled -eq $false)
    } catch {return $false}
}
try {
    Write-MonitorLog 'Monitor started.'
    do {
        if(!(Test-Bridge)){
            Write-MonitorLog 'Bridge unavailable; starting existing bridge launchers.'
            try {
                & wsl.exe -d Ubuntu -- true
                if($LASTEXITCODE -ne 0){throw 'Ubuntu could not start.'}
                try { & "$project/scripts/start-home-wifi.ps1" -HomeOnly *>> $log }
                catch { Write-MonitorLog "Home launcher: $($_.Exception.Message)" }
                # Private access can still recover if the home LAN address has changed.
                & "$project/scripts/start-tailscale.ps1" -BridgeReady *>> $log
                if(Test-Bridge){Write-MonitorLog 'Private HTTPS bridge healthy.'}
                else {Write-MonitorLog 'Bridge still unavailable; will retry.'}
            } catch {Write-MonitorLog "Recovery: $($_.Exception.Message)"}
        }
        if(!$Once){Start-Sleep -Seconds 30}
    } while(!$Once)
} finally {$mutex.ReleaseMutex();$mutex.Dispose()}
