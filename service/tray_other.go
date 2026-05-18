//go:build !windows && !linux && !darwin

package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

// Fallback for unsupported platforms.

func RunTray(tooltip string, onShow func(), onExit func()) {
	fmt.Printf("[%s] Service running in background (no tray on this platform)\n", tooltip)
	select {}
}

func LaunchUI() {
	LaunchUIWithArgs()
}

func LaunchUIWithArgs(args ...string) {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow")
	if _, err := os.Stat(uiExe); err != nil {
		uiExe = "./Flow"
	}
	cmd := exec.Command(uiExe, args...)
	cmd.Dir = dir
	cmd.Start()
}

func LaunchUIWithDownload(url string) {
	LaunchUIWithArgs("--add-download", url)
}
