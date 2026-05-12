pub fn set_windows_auto_start(app_name: &str, exe_path: &str, enabled: bool) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let key = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
        let status = if enabled {
            std::process::Command::new("reg")
                .args(["add", key, "/v", app_name, "/t", "REG_SZ", "/d", exe_path, "/f"])
                .status()
                .map_err(|e| e.to_string())?
        } else {
            std::process::Command::new("reg")
                .args(["delete", key, "/v", app_name, "/f"])
                .status()
                .map_err(|e| e.to_string())?
        };
        if status.success() {
            Ok(())
        } else {
            Err("Failed to update Windows startup registry".to_string())
        }
    }

    #[cfg(not(target_os = "windows"))]
    {
        let _ = (app_name, exe_path, enabled);
        Err("Auto start is currently implemented only for Windows".to_string())
    }
}

pub fn is_windows_auto_start_enabled(app_name: &str) -> Result<bool, String> {
    #[cfg(target_os = "windows")]
    {
        let key = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
        let output = std::process::Command::new("reg")
            .args(["query", key, "/v", app_name])
            .output()
            .map_err(|e| e.to_string())?;
        Ok(output.status.success())
    }

    #[cfg(not(target_os = "windows"))]
    {
        let _ = app_name;
        Ok(false)
    }
}
