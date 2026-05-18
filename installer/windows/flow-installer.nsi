; Flow Download Manager - NSIS Installer Script
; Packages: flow-service.exe + Flow.exe + browser extension native messaging host

!include "MUI2.nsh"
!include "FileFunc.nsh"

; --- General ---
Name "Flow Download Manager"
OutFile "FlowSetup.exe"
InstallDir "$PROGRAMFILES64\Flow"
InstallDirRegKey HKLM "Software\Flow" "InstallDir"
RequestExecutionLevel admin

; --- Version Info ---
!define PRODUCT_NAME "Flow Download Manager"
!define PRODUCT_VERSION "1.0.0"
!define PRODUCT_PUBLISHER "FlowSpeed"
!define PRODUCT_WEB_SITE "https://github.com/AminBhst/IDM"

VIProductVersion "${PRODUCT_VERSION}.0"
VIAddVersionKey "ProductName" "${PRODUCT_NAME}"
VIAddVersionKey "ProductVersion" "${PRODUCT_VERSION}"
VIAddVersionKey "CompanyName" "${PRODUCT_PUBLISHER}"
VIAddVersionKey "FileDescription" "Flow Download Manager Installer"

; --- Interface ---
!define MUI_ICON "..\..\assets\logo\logo_square.png"
!define MUI_ABORTWARNING

; --- Pages ---
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

; --- Sections ---
Section "Flow Download Manager" SecMain
  SectionIn RO

  SetOutPath "$INSTDIR"

  ; Service binary
  File "..\..\service\flow-service.exe"

  ; UI binary (Kotlin/Compose Desktop)
  File "..\..\desktop\app\build\compose\binaries\main\app\Flow.exe"
  File /r "..\..\desktop\app\build\compose\binaries\main\app\runtime\*.*"

  ; Icon
  File "..\..\desktop\app\icons\icon.ico"

  ; Create start menu shortcuts
  CreateDirectory "$SMPROGRAMS\Flow"
  CreateShortcut "$SMPROGRAMS\Flow\Flow Download Manager.lnk" "$INSTDIR\Flow.exe" "" "$INSTDIR\icon.ico"
  CreateShortcut "$SMPROGRAMS\Flow\Uninstall.lnk" "$INSTDIR\uninstall.exe"

  ; Desktop shortcut
  CreateShortcut "$DESKTOP\Flow Download Manager.lnk" "$INSTDIR\Flow.exe" "" "$INSTDIR\icon.ico"

  ; Register auto-start for service (runs at login)
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "FlowService" '"$INSTDIR\flow-service.exe" --background'

  ; Browser extension native messaging host manifest (Chrome)
  SetOutPath "$INSTDIR\native-messaging"
  FileOpen $0 "$INSTDIR\native-messaging\com.flowspeed.link.json" w
  FileWrite $0 '{"name":"com.flowspeed.link","description":"Flow Download Manager","path":"$INSTDIR\\flow-service.exe","type":"stdio","allowed_origins":["chrome-extension://EXTENSION_ID/"]}'
  FileClose $0

  ; Register native messaging host in registry (Chrome)
  WriteRegStr HKCU "Software\Google\Chrome\NativeMessagingHosts\com.flowspeed.link" "" "$INSTDIR\native-messaging\com.flowspeed.link.json"

  ; Register native messaging host (Firefox)
  WriteRegStr HKCU "Software\Mozilla\NativeMessagingHosts\com.flowspeed.link" "" "$INSTDIR\native-messaging\com.flowspeed.link.json"

  ; Uninstaller
  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; Registry info for Add/Remove Programs
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "DisplayName" "${PRODUCT_NAME}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "Publisher" "${PRODUCT_PUBLISHER}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow" "NoRepair" 1

  ; Start the service immediately
  Exec '"$INSTDIR\flow-service.exe" --background'

SectionEnd

; --- Uninstaller ---
Section "Uninstall"
  ; Kill running processes
  nsExec::ExecToLog 'taskkill /F /IM flow-service.exe'
  nsExec::ExecToLog 'taskkill /F /IM Flow.exe'

  ; Remove auto-start
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "FlowService"

  ; Remove native messaging host registrations
  DeleteRegKey HKCU "Software\Google\Chrome\NativeMessagingHosts\com.flowspeed.link"
  DeleteRegKey HKCU "Software\Mozilla\NativeMessagingHosts\com.flowspeed.link"

  ; Remove files
  RMDir /r "$INSTDIR"

  ; Remove shortcuts
  RMDir /r "$SMPROGRAMS\Flow"
  Delete "$DESKTOP\Flow Download Manager.lnk"

  ; Remove registry entries
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Flow"
  DeleteRegKey HKLM "Software\Flow"

SectionEnd
