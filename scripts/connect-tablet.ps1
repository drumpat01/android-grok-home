param([string]$Serial='',[int]$TimeoutSeconds=20,[switch]$Watch,[switch]$LibraryOnly)
$ErrorActionPreference='Stop'
$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$tabletHardwareSerial=$env:GROK_HOME_TABLET_SERIAL
if(!$tabletHardwareSerial){throw 'Set GROK_HOME_TABLET_SERIAL to the hardware serial of your paired Android device.'}
function Get-TabletDiscoveryCandidates([string[]]$Lines,[string]$HardwareSerial=$tabletHardwareSerial){
    foreach($line in $Lines){
        if($line -match '^\s*(\S+)\s+_adb-tls-connect\._tcp\.?\s+(\S+:\d+)\s*$'){
            $service=$Matches[1];$endpoint=$Matches[2]
            if($service.StartsWith("adb-$HardwareSerial-",[StringComparison]::OrdinalIgnoreCase)){$endpoint}
        }
    }
}
function Test-OurTablet([string]$Target){
    $hardware=(& $adb -s $Target shell getprop ro.serialno 2>$null | Out-String).Trim()
    if($LASTEXITCODE-ne 0 -or $hardware-ne $tabletHardwareSerial){return $false}
    return $true
}
function Find-OurTablet {
    foreach($line in @(& $adb devices 2>$null)){
        if($line -match '^(\S+)\s+device\s*$' -and !$Matches[1].StartsWith('emulator-')){
            $target=$Matches[1];if(Test-OurTablet $target){return $target}
        }
    }
    $candidates=@(Get-TabletDiscoveryCandidates @(& $adb mdns services 2>$null)) | Select-Object -Unique
    foreach($endpoint in $candidates){
        & $adb connect $endpoint 2>$null | Out-Null
        if(Test-OurTablet $endpoint){return $endpoint}
    }
    return $null
}
if($LibraryOnly){return}
if($Serial){
    if(!(Test-OurTablet $Serial)){
        if($Serial-match '^\S+:\d+$'){& $adb connect $Serial 2>$null | Out-Null}
        if(!(Test-OurTablet $Serial)){throw 'The specified connection is not the configured Android device.'}
    }
    Write-Output $Serial;return
}
$deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$last=''
do {
    $target=Find-OurTablet
    if($Watch){if($target -and $target-ne $last){Write-Output 'Android device automatically connected.'};$last=$target;Start-Sleep -Seconds 20}
    elseif($target){Write-Output $target;return}
    elseif([DateTime]::UtcNow-lt $deadline){Start-Sleep -Seconds 2}
}while($Watch -or [DateTime]::UtcNow-lt $deadline)
throw 'Tablet not discovered. Turn it on, enable Wireless debugging, and use the same home network. Existing pairing is retained; no port number is needed when discovery is available.'
