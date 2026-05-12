#define MyAppName "Flow Download Manager"
#define MyAppVersion "0.1.0"
#define MyAppPublisher "Flow"
#define SourceRoot ".."
#define BuildDir SourceRoot + "\app\target\release"

[Setup]
AppId={{8C42C29C-05A5-4F5E-9C6B-5CC627A9D0D8}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Flow
DefaultGroupName={#MyAppName}
OutputDir=.
OutputBaseFilename=FlowSetup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible

[Files]
Source: "{#BuildDir}\flow-ui.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#BuildDir}\flow-host.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourceRoot}\native-messaging\windows\com.flow.download_manager.json"; DestDir: "{app}\native-messaging\windows"; Flags: ignoreversion
Source: "{#SourceRoot}\native-messaging\windows\com.flow.download_manager.firefox.json"; DestDir: "{app}\native-messaging\windows"; Flags: ignoreversion
Source: "{#SourceRoot}\native-messaging\windows\register-host.ps1"; DestDir: "{app}\native-messaging\windows"; Flags: ignoreversion
Source: "{#SourceRoot}\native-messaging\windows\unregister-host.ps1"; DestDir: "{app}\native-messaging\windows"; Flags: ignoreversion
Source: "{#SourceRoot}\extension\*"; DestDir: "{app}\extension"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Flow"; Filename: "{app}\flow-ui.exe"
Name: "{autodesktop}\Flow"; Filename: "{app}\flow-ui.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\flow-host.exe"; Parameters: "--health"; Flags: runhidden
Filename: "powershell.exe"; Parameters: "-ExecutionPolicy Bypass -File ""{app}\native-messaging\windows\register-host.ps1"" -ExtensionId ""ndlghhcdbcemhhnggkmckhnnfbnigpka"" -FirefoxExtensionId ""flow_download_manager@example.com"""; Flags: runhidden

[UninstallRun]
Filename: "taskkill.exe"; Parameters: "/F /IM flow-ui.exe"; Flags: runhidden; RunOnceId: "KillFlowUI"
Filename: "taskkill.exe"; Parameters: "/F /IM flow-host.exe"; Flags: runhidden; RunOnceId: "KillFlowHost"
Filename: "powershell.exe"; Parameters: "-ExecutionPolicy Bypass -File ""{app}\native-messaging\windows\unregister-host.ps1"""; Flags: runhidden; RunOnceId: "FlowUnregisterNativeHost"

[UninstallDelete]
Type: filesandordirs; Name: "{localappdata}\Flow\NativeMessaging"
