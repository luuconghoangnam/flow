[Setup]
AppName=Flow
AppVersion=1.0.0
AppPublisher=FlowSpeed
AppPublisherURL=https://flowspeed.link
DefaultDirName={autopf}\Flow
DefaultGroupName=Flow
OutputDir=..\build\installer
OutputBaseFilename=Flow-Setup-1.0.0
Compression=lzma2/ultra64
SolidCompression=yes
SetupIconFile=..\desktop\app\icons\icon.ico
UninstallDisplayIcon={app}\Flow.exe
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
DisableProgramGroupPage=yes

[Files]
Source: "..\desktop\app\build\compose\binaries\main-release\app\Flow\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\Flow"; Filename: "{app}\Flow.exe"
Name: "{autodesktop}\Flow"; Filename: "{app}\Flow.exe"
Name: "{userstartup}\Flow"; Filename: "{app}\Flow.exe"; Parameters: "--background"

[Run]
Filename: "{app}\Flow.exe"; Description: "Launch Flow"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{userappdata}\.flow"
