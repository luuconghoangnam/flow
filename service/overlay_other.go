//go:build !windows

package main

import (
	"os/exec"
	"strings"
)

// showFolderDialog opens a folder picker on Linux/macOS.
func showFolderDialog() string {
	// Try zenity (Linux GTK)
	if path, err := exec.Command("zenity", "--file-selection", "--directory", "--title=Choose Download Folder").Output(); err == nil {
		return strings.TrimSpace(string(path))
	}
	// Try kdialog (Linux KDE)
	if path, err := exec.Command("kdialog", "--getexistingdirectory", defaultDownloadFolder()).Output(); err == nil {
		return strings.TrimSpace(string(path))
	}
	// macOS: use osascript
	script := `tell application "System Events" to choose folder with prompt "Choose Download Folder"`
	if path, err := exec.Command("osascript", "-e", script).Output(); err == nil {
		result := strings.TrimSpace(string(path))
		// Convert "alias Macintosh HD:Users:..." to POSIX path
		if strings.HasPrefix(result, "alias ") {
			script2 := `tell application "System Events" to POSIX path of (choose folder with prompt "Choose Download Folder")`
			if path2, err := exec.Command("osascript", "-e", script2).Output(); err == nil {
				return strings.TrimSpace(string(path2))
			}
		}
		return result
	}
	return ""
}
