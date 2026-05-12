$ErrorActionPreference = "Stop"

$chromeKey = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.flow.download_manager"
$edgeKey = "HKCU:\Software\Microsoft\Edge\NativeMessagingHosts\com.flow.download_manager"

Remove-Item -Path $chromeKey -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path $edgeKey -Recurse -Force -ErrorAction SilentlyContinue

"Unregistered Flow native host."
