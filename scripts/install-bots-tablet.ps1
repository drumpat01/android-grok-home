param([string]$Serial='')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$apk=Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
if(!(Test-Path $apk)){throw 'Run ./gradlew assembleDebug first.'}
$Serial=& "$PSScriptRoot/connect-tablet.ps1" -Serial $Serial
& $adb -s $Serial install -r $apk
if($LASTEXITCODE -ne 0){throw 'APK installation failed.'}
& $adb -s $Serial push $apk /sdcard/Download/GrokHome-bots-debug.apk
if($LASTEXITCODE -ne 0){throw 'Installed app, but APK download-folder copy failed.'}
& $adb -s $Serial shell am start -W -n dev.grokhome/.HomeActivity
if($LASTEXITCODE -ne 0){throw 'Installed app, but launch failed.'}
Write-Host 'Installed. Open Bots in the top-left corner and choose Max. USB can then be unplugged.'
