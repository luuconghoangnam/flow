//go:build darwin

package main

import (
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"syscall"
)

// macOS tray implementation.
// Full NSStatusItem support requires CGo + Cocoa (Sprint 4 enhancement).
// For now, the service runs as a background daemon (launchd agent)
// and responds to signals.

func RunTray(tooltip string, onShow func(), onExit func()) {
	fmt.Printf("[%s] Service running (macOS background mode)\n", tooltip)

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
	// On macOS, the UI app is typically in an .app bundle
	uiExe := filepath.Join(dir, "Flow")
	if _, err := os.Stat(uiExe); err != nil {
		// Try .app bundle
		appBundle := filepath.Join(dir, "Flow.app", "Contents", "MacOS", "Flow")
		if _, err := os.Stat(appBundle); err == nil {
			uiExe = appBundle
		} else {
			uiExe = "flow"
		}
	}
	cmd := exec.Command(uiExe, args...)
	cmd.Dir = dir
	cmd.Start()
}

func LaunchUIWithDownload(url string) {
	LaunchUIWithArgs("--add-download", url)
}
