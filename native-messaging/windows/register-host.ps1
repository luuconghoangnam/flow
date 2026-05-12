param(
    [Parameter(Mandatory = $true)]
    [string]$ExtensionId,

    [string]$FirefoxExtensionId = "",

    [string]$HostExe = "$PSScriptRoot\..\..\app\target\debug\flow-host.exe",
    [string]$ManifestOut = "$env:LOCALAPPDATA\Flow\NativeMessaging\com.flow.download_manager.json",
    [string]$FirefoxManifestOut = "$env:LOCALAPPDATA\Flow\NativeMessaging\com.flow.download_manager.firefox.json"
)

$ErrorActionPreference = "Stop"

$manifestTemplate = Join-Path $PSScriptRoot "com.flow.download_manager.json"
$firefoxTemplate = Join-Path $PSScriptRoot "com.flow.download_manager.firefox.json"
$manifestDir = Split-Path -Parent $ManifestOut
New-Item -ItemType Directory -Force -Path $manifestDir | Out-Null

$hostPath = (Resolve-Path -LiteralPath $HostExe).Path.Replace("\", "\\")
$content = Get-Content -LiteralPath $manifestTemplate -Raw
$content = $content.Replace("__FLOW_HOST_PATH__", $hostPath)
$content = $content.Replace("__EXTENSION_ID__", $ExtensionId)
Set-Content -LiteralPath $ManifestOut -Value $content -Encoding UTF8

if ($FirefoxExtensionId -ne "") {
    $firefoxContent = Get-Content -LiteralPath $firefoxTemplate -Raw
    $firefoxContent = $firefoxContent.Replace("__FLOW_HOST_PATH__", $hostPath)
    $firefoxContent = $firefoxContent.Replace("__FIREFOX_EXTENSION_ID__", $FirefoxExtensionId)
    Set-Content -LiteralPath $FirefoxManifestOut -Value $firefoxContent -Encoding UTF8
}

$chromeKey = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.flow.download_manager"
$edgeKey = "HKCU:\Software\Microsoft\Edge\NativeMessagingHosts\com.flow.download_manager"
$firefoxKey = "HKCU:\Software\Mozilla\NativeMessagingHosts\com.flow.download_manager"

New-Item -Force -Path $chromeKey | Out-Null
New-Item -Force -Path $edgeKey | Out-Null
Set-Item -Path $chromeKey -Value $ManifestOut
Set-Item -Path $edgeKey -Value $ManifestOut

if ($FirefoxExtensionId -ne "") {
    New-Item -Force -Path $firefoxKey | Out-Null
    Set-Item -Path $firefoxKey -Value $FirefoxManifestOut
}

"Registered Flow native host: $ManifestOut"
