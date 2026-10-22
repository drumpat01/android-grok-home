param(
    [string]$Serial = '',          # adb serial or ip:port of the tablet; leave empty to only publish
    [switch]$NoPublish
)
# Windows side of a roster change: copy the built APK, publish it for Settings > Update app, and optionally install over adb.
# Run from PowerShell after scripts/roster/ship.sh succeeded:
#   powershell -ExecutionPolicy Bypass -File scripts\roster\ship.ps1 [-Serial <adb address, for example 192.168.1.50:5555>]
$ErrorActionPreference = 'Stop'
$project = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$apk = Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
if (!(Test-Path $apk)) { throw 'No APK. Run scripts/roster/ship.sh first.' }
$aapt = Get-ChildItem "$env:LOCALAPPDATA/Android/Sdk/build-tools/*/aapt.exe" | Sort-Object FullName -Descending | Select-Object -First 1
$badging = (& $aapt.FullName dump badging $apk | Out-String)
if ($badging -notmatch "package: name='dev.grokhome' versionCode='(\d+)' versionName='([^']+)'") { throw 'APK is not Grok Home.' }
$name = $Matches[2]
$dst = Join-Path ([Environment]::GetFolderPath('UserProfile')) "Downloads\GrokHome-$name.apk"
Copy-Item $apk $dst -Force
New-Item -ItemType Directory -Force (Join-Path $project 'artifacts') | Out-Null
Copy-Item $apk (Join-Path $project 'artifacts/GrokHome-bots-debug.apk') -Force
Write-Host "APK $name -> $dst"
if (!$NoPublish) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'scripts/publish-app-update.ps1') -Apk $dst
}
if ($Serial) {
    $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') { & $adb connect $Serial | Out-Null }
    & $adb -s $Serial install -r $dst
    & $adb -s $Serial shell dumpsys package dev.grokhome | Select-String versionName
    & $adb -s $Serial shell monkey -p dev.grokhome -c android.intent.category.LAUNCHER 1 | Out-Null
}
else {
    Write-Host 'Not installed. On the tablet: Settings > Update app, or re-run with -Serial <adb address>.'
}
