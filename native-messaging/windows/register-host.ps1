param(
    [Parameter(Mandatory = $true)]
    [string]$ExtensionId,

    [string]$HostExe = "$PSScriptRoot\..\..\app\target\debug\flow-host.exe",
    [string]$ManifestOut = "$env:LOCALAPPDATA\Flow\NativeMessaging\com.flow.download_manager.json"
)

$ErrorActionPreference = "Stop"

$manifestTemplate = Join-Path $PSScriptRoot "com.flow.download_manager.json"
$manifestDir = Split-Path -Parent $ManifestOut
New-Item -ItemType Directory -Force -Path $manifestDir | Out-Null

$hostPath = (Resolve-Path -LiteralPath $HostExe).Path.Replace("\", "\\")
$content = Get-Content -LiteralPath $manifestTemplate -Raw
$content = $content.Replace("__FLOW_HOST_PATH__", $hostPath)
$content = $content.Replace("__EXTENSION_ID__", $ExtensionId)
Set-Content -LiteralPath $ManifestOut -Value $content -Encoding UTF8

$chromeKey = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.flow.download_manager"
$edgeKey = "HKCU:\Software\Microsoft\Edge\NativeMessagingHosts\com.flow.download_manager"

New-Item -Force -Path $chromeKey | Out-Null
New-Item -Force -Path $edgeKey | Out-Null
Set-Item -Path $chromeKey -Value $ManifestOut
Set-Item -Path $edgeKey -Value $ManifestOut

"Registered Flow native host: $ManifestOut"
