. (Join-Path $PSScriptRoot 'common.ps1')
# Run with administrator rights. This changes only the named inbound rule.
$ErrorActionPreference='Stop'
$network=Get-GrokNetwork
$ruleName='GrokHomeVoice-HomeSubnet-8788'
$python=Get-GrokPython
$project=Split-Path $PSScriptRoot -Parent
$null=New-Item -ItemType Directory -Force (Join-Path $project '.tools')
try{
    $existing=Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
    if(!$existing){
        New-NetFirewallRule -Name $ruleName -DisplayName 'Grok Home voice on home Wi-Fi' -Direction Inbound -Action Allow -Protocol TCP -LocalAddress $network.Address -LocalPort 8788 -RemoteAddress $network.Subnet -Program $python -Profile Any -Enabled True | Out-Null
    }
    Get-NetFirewallRule -Name $ruleName -ErrorAction Stop | Out-Null
    @{status='configured';address=$network.Address;port=8788;subnet=$network.Subnet} | ConvertTo-Json | Set-Content (Join-Path $project '.tools/wifi-firewall-status.json') -Encoding UTF8
}catch{
    @{status='failed';message=$_.Exception.Message} | ConvertTo-Json | Set-Content (Join-Path $project '.tools/wifi-firewall-status.json') -Encoding UTF8
    throw
}
