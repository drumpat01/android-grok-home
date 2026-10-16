param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
$project = Split-Path $PSScriptRoot -Parent
$linuxProject = Get-GrokLinuxProject $project
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$emulator = "$env:LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"
$apk = Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
$keyFile = Join-Path $project 'backend/.env'
if ($Serial -notlike 'emulator-*') { throw 'This helper installs only to an emulator. Tablet installation is a separate step.' }
if (!(Test-Path $apk)) { throw 'Build the voice APK first; see README-VOICE.md.' }
if (!(Test-Path $keyFile)) { Copy-Item (Join-Path $project 'backend/.env.example') $keyFile }
$health = $null
try { $health = Invoke-RestMethod http://127.0.0.1:8787/health -TimeoutSec 2 } catch { }
if ($health -and $health.service -ne 'grok-home-voice') { throw 'Port 8787 is used by another service.' }
if (!$health) {
    Start-Process -FilePath wsl.exe -WorkingDirectory $env:USERPROFILE -WindowStyle Hidden -ArgumentList '-d','Ubuntu','--','python3',"$linuxProject/backend/server.py" -RedirectStandardOutput "$env:TEMP/grok-voice-broker-8787.log" -RedirectStandardError "$env:TEMP/grok-voice-broker-8787-error.log"
    for ($attempt=0; $attempt -lt 15; $attempt++) {
        try { $health=Invoke-RestMethod http://127.0.0.1:8787/health -TimeoutSec 2; break } catch { Start-Sleep -Milliseconds 500 }
    }
}
if (!$health -or $health.service -ne 'grok-home-voice') { throw 'Voice server did not start. Check the broker log in your Temp folder.' }
$devices = & $adb devices
if (!($devices -match "^$([regex]::Escape($Serial))\s+")) {
    if ($Serial -ne 'emulator-5554') { throw 'Start the requested emulator first.' }
    Start-Process -FilePath $emulator -WorkingDirectory $env:USERPROFILE -WindowStyle Hidden -ArgumentList '-avd','GrokHomeOffline','-gpu','swiftshader_indirect','-no-snapshot-load' -RedirectStandardOutput "$env:TEMP/grok-voice-emulator.log" -RedirectStandardError "$env:TEMP/grok-voice-emulator-error.log"
}
$booted = $false
for ($attempt=0; $attempt -lt 60; $attempt++) {
    $boot = & $adb -s $Serial shell getprop sys.boot_completed 2>$null
    if ($boot -match '^1') { $booted=$true; break }
    Start-Sleep -Seconds 1
}
if (!$booted) { throw 'The emulator is still booting. Run this helper again when it is ready.' }
& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb -s $Serial reverse tcp:8787 tcp:8787
if ($LASTEXITCODE -ne 0) { throw 'Local server forwarding failed.' }
& $adb -s $Serial emu avd hostmicon
& $adb -s $Serial shell input keyevent 82
& $adb -s $Serial shell am start -W -n dev.grokhome/.HomeActivity
if (!$health.key_configured) { Write-Host "Add your xAI API key to: $keyFile" }
Write-Host 'Voice server is ready. Tap the microphone in Grok Home to connect.'
Write-Host 'Emulator host microphone input is enabled. If needed, check Windows microphone access for desktop apps.'
