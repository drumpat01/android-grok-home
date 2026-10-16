param([string]$Apk='')
. (Join-Path $PSScriptRoot 'common.ps1')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$Apk){$Apk=Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'}
$aapt=Get-ChildItem "$env:LOCALAPPDATA/Android/Sdk/build-tools/*/aapt.exe" | Sort-Object FullName -Descending | Select-Object -First 1
if(!$aapt){throw 'Android build-tools/aapt is required to publish APK metadata.'}
$badging=(& $aapt.FullName dump badging $Apk | Out-String)
if($LASTEXITCODE-ne 0 -or $badging-notmatch "package: name='dev.grokhome' versionCode='(\d+)' versionName='([^']+)'"){throw 'APK package metadata did not match Grok Home.'}
$code=$Matches[1];$name=$Matches[2]
$python=Get-GrokPython
& $python (Join-Path $project 'backend/app_updates.py') $Apk --version-code $code --version-name $name
if($LASTEXITCODE-ne 0){throw 'APK publication failed.'}
