//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

// showFolderDialog opens a folder picker on Windows.
// Uses PowerShell's folder browser dialog to avoid unsafe.Pointer COM interop issues.
func showFolderDialog() string {
	// Use PowerShell to show a folder browser dialog — simple and reliable
	script := `Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; $f.Description = 'Choose Download Folder'; $f.RootFolder = 'MyComputer'; if ($f.ShowDialog() -eq 'OK') { $f.SelectedPath }`
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	result := strings.TrimSpace(string(out))
	if result == "" {
		return ""
	}
	// Verify it's a valid directory
	if info, err := os.Stat(result); err == nil && info.IsDir() {
		return result
	}
	return result
}

// defaultExeDir returns the directory of the current executable.
func defaultExeDir() string {
	exe, _ := os.Executable()
	return filepath.Dir(exe)
}
