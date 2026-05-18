//go:build linux

package main

import (
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"syscall"
)

// Linux tray implementation using AppIndicator via command-line tools.
// For a production build, this would use D-Bus StatusNotifierItem protocol
// or libappindicator via CGo. For now, we use a signal-based approach
// and rely on the desktop file for tray integration.

// RunTray on Linux: uses a simple signal handler approach.
// Full AppIndicator support requires CGo + libappindicator3 (Sprint 4 enhancement).
// For now, the service runs as a background daemon and responds to signals.
func RunTray(tooltip string, onShow func(), onExit func()) {
	fmt.Printf("[%s] Service running (Linux background mode)\n", tooltip)

	// Handle signals for graceful shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGTERM, syscall.SIGINT, syscall.SIGUSR1)

	for sig := range sigCh {
		switch sig {
		case syscall.SIGUSR1:
			// USR1 signal = show UI
			if onShow != nil {
				go onShow()
			}
		case syscall.SIGTERM, syscall.SIGINT:
			// Graceful shutdown
			if onExit != nil {
				onExit()
			}
			return
		}
	}
}

func LaunchUI() {
	LaunchUIWithArgs()
}

func LaunchUIWithArgs(args ...string) {
	exeDir, _ := os.Executable()
	dir := filepath.Dir(exeDir)
	uiExe := filepath.Join(dir, "Flow")
	if _, err := os.Stat(uiExe); err != nil {
		// Try finding in PATH
		uiExe = "flow"
	}
	cmd := exec.Command(uiExe, args...)
	cmd.Dir = dir
	cmd.Start()
}

func LaunchUIWithDownload(url string) {
	LaunchUIWithArgs("--add-download", url)
}
