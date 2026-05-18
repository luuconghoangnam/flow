//go:build !windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

// Non-Windows: no system tray, just block until signal.
// On Linux/macOS, the tray would use dbus/AppIndicator or NSStatusItem.
// For now, just print status and wait.

func RunTray(tooltip string, onShow func(), onExit func()) {
	fmt.Printf("[%s] Service running in background (no tray on this platform)\n", tooltip)
	// Block forever - main() handles signals
	select {}
}

func LaunchUI() {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow")
	if _, err := os.Stat(uiExe); err != nil {
		uiExe = "./Flow"
	}
	cmd := exec.Command(uiExe)
	cmd.Dir = dir
	cmd.Start()
}

func LaunchUIWithDownload(url string) {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow")
	if _, err := os.Stat(uiExe); err != nil {
		uiExe = "./Flow"
	}
	cmd := exec.Command(uiExe, "--add-download", url)
	cmd.Dir = dir
	cmd.Start()
}
