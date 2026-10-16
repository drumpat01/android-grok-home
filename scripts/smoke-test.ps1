param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Serial -notlike 'emulator-*') { throw 'This smoke test only targets an emulator.' }
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$project = Split-Path $PSScriptRoot -Parent
$out = Join-Path $project 'artifacts'
function Adb { & $adb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
function Nodes {
    Adb shell uiautomator dump /sdcard/grok-test.xml | Out-Null
    Adb pull /sdcard/grok-test.xml "$out/ui.xml" | Out-Null
    [xml]$xml = Get-Content "$out/ui.xml" -Raw
    return $xml.SelectNodes('//node')
}
function Find([string]$label) { $node = Nodes | Where-Object { $_.text -eq $label -or $_.'content-desc' -eq $label } | Select-Object -First 1; if (!$node) { throw "Missing UI: $label" }; return $node }
function Tap([string]$label, [string]$class = '') {
    $node = if ($class) { Nodes | Where-Object { $_.text -eq $label -and $_.class -eq $class } | Select-Object -First 1 } else { Find $label }; if (!$node) { throw "Missing UI: $label / $class" }
    $coords = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Adb shell input tap ([int](($coords[0]+$coords[2])/2)) ([int](($coords[1]+$coords[3])/2))
}
function SettingsFocused { for ($attempt=0; $attempt -lt 10; $attempt++) { $focused=Adb shell dumpsys window; if ($focused -match 'mCurrentFocus=.*com.android.settings') { return $true }; Start-Sleep -Milliseconds 300 }; return $false }
function Capture([string]$name) { Adb shell screencap -p /sdcard/grok-test.png; Adb pull /sdcard/grok-test.png "$out/$name.png" | Out-Null }
Adb shell am start -W -n dev.grokhome/.HomeActivity | Out-Null
Find 'Ready when you are' | Out-Null
Capture 'home'
Adb shell input swipe 2550 700 2000 700 350
Find 'Automatically listen on Home' | Out-Null
$initial = (Find 'Automatically listen on Home').checked
Tap 'Automatically listen on Home'
$expected = if ($initial -eq 'true') {'false'} else {'true'}
if ((Find 'Automatically listen on Home').checked -ne $expected) { throw 'Auto-listen toggle failed' }
Tap 'Mute microphone'
$muted = (Find 'Mute microphone').checked
Capture 'drawer'
Adb shell am force-stop dev.grokhome
Adb shell am start -W -n dev.grokhome/.HomeActivity | Out-Null
Tap 'Settings'
if ((Find 'Automatically listen on Home').checked -ne $expected) { throw 'Auto-listen did not persist across process restart' }
if ((Find 'Mute microphone').checked -ne $muted) { throw 'Mute did not persist across process restart' }
Adb shell input swipe 2100 700 2520 700 350
Find 'Apps' | Out-Null
if (Nodes | Where-Object { $_.text -eq 'Automatically listen on Home' }) { throw 'Outward drawer swipe failed' }
Tap 'Settings'
Adb shell input tap 300 300
if (Nodes | Where-Object { $_.text -eq 'Automatically listen on Home' }) { throw 'Scrim dismissal failed' }
Tap 'Apps'
Find 'Search apps' | Out-Null
Capture 'apps'
Tap 'Search apps'
Adb shell input text Settings
Find 'Settings' | Out-Null
Tap 'Settings' 'android.widget.TextView'
if (!(SettingsFocused)) { throw 'App picker did not launch Settings' }
Adb shell am start -W -n dev.grokhome/.HomeActivity | Out-Null
Tap 'Settings'
Tap 'All Android settings'
if (!(SettingsFocused)) { throw 'Android settings shortcut failed' }
Adb shell am start -W -n dev.grokhome/.HomeActivity | Out-Null
Tap 'Keyboard'
Find 'Offline note' | Out-Null
Tap 'Voice and chat are not connected'
Adb shell input text OfflineSmoke
Tap 'Save'
Tap 'Keyboard'
Find 'OfflineSmoke' | Out-Null
Tap 'Cancel'
Tap 'Microphone'
Find 'Offline preview' | Out-Null
Tap 'OK'
Tap 'History'
Find 'History' | Out-Null
Tap 'Done'
Adb shell input tap 1280 640
Find 'Speaking preview' | Out-Null
Capture 'speaking'
Adb shell input tap 1280 640
Find 'Ready when you are' | Out-Null
$crashes = Adb logcat -d -s AndroidRuntime:E
if ($crashes -match 'Process: dev.grokhome') { throw 'App crash found in logcat' }
'PASS: drawer swipes/scrim, app search and launch, settings shortcuts, process-restart persistence, offline controls, motion preview, no app crashes.'
