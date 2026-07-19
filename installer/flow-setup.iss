[Setup]
AppName={{app_display_name}}
AppVersion={{app_version}}
AppPublisher={{app_publisher}}
AppPublisherURL=https://{{project_website}}
AppSupportURL=https://{{project_website}}
AppUpdatesURL={{source_code_url}}
DefaultDirName={{app_install_dir}}
DefaultGroupName={{app_display_name}}
OutputDir={{output_dir}}
OutputBaseFilename={{output_base_filename}}
Compression=lzma2/ultra64
SolidCompression=yes
SetupIconFile={{icon_file}}
UninstallDisplayIcon={{app_exe}}
LicenseFile={{license_file}}
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
DisableProgramGroupPage=yes

[Files]
Source: "{{input_dir}}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{{app_group_icon}}"; Filename: "{{app_exe}}"
Name: "{{app_desktop_icon}}"; Filename: "{{app_exe}}"
Name: "{{app_startup_icon}}"; Filename: "{{app_exe}}"; Parameters: "--background"

[Run]
Filename: "{{app_exe}}"; Description: "Launch {{app_display_name}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{{app_data_dir}}"
