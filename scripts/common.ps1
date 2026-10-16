$ErrorActionPreference='Stop'
function Get-GrokPython {
    if($env:GROK_HOME_PYTHON){return (Get-Item -LiteralPath $env:GROK_HOME_PYTHON -ErrorAction Stop).FullName}
    foreach($name in @('python.exe','python3.exe')){
        $command=Get-Command $name -ErrorAction SilentlyContinue
        if($command -and $command.Source -notlike '*WindowsApps*'){return $command.Source}
    }
    throw 'Install Python 3 and add it to PATH, or set GROK_HOME_PYTHON to its executable.'
}
function Get-GrokLinuxProject([string]$Project){
    if($Project -match '^\\\\wsl(?:\.localhost|\$)\\([^\\]+)\\(.+)$'){
        if($Matches[1] -ne 'Ubuntu'){throw 'These helpers require the Ubuntu WSL distribution.'}
        return '/'+$Matches[2].Replace('\','/')
    }
    $converted=(& wsl.exe -d Ubuntu -- wslpath -u $Project | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or !$converted){throw 'Cannot resolve project path in Ubuntu WSL.'}
    return $converted
}
function Get-GrokNetwork {
    $address=$env:GROK_HOME_ADDRESS
    $subnet=$env:GROK_HOME_SUBNET
    if(!$address -or !$subnet){throw 'Set GROK_HOME_ADDRESS to this computer LAN IPv4 and GROK_HOME_SUBNET to its private CIDR (for example 192.168.1.10 and 192.168.1.0/24).'}
    return @{Address=$address;Subnet=$subnet}
}
