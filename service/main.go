// Flow Download Manager - Native Background Service
//
// Lightweight service (~5-10MB RAM) that handles:
// - Browser extension HTTP requests (download interception)
// - Multi-part concurrent download engine with resume
// - Download queue management
// - IPC for UI process communication
//
// Cross-platform: Windows, Linux, macOS
package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	port := flag.Int("port", 15151, "Integration HTTP server port (browser extension)")
	ipcPort := flag.Int("ipc-port", 15152, "IPC server port (UI communication)")
	dataDir := flag.String("data-dir", defaultDataDir(), "Data directory path")
	background := flag.Bool("background", false, "Run in background mode (no console output)")
	flag.Parse()

	cfg := &Config{
		IntegrationPort: *port,
		IpcPort:         *ipcPort,
		DataDir:         *dataDir,
		Background:      *background,
	}

	// Single instance lock
	lock, err := AcquireLock(cfg.DataDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Another instance is running: %v\n", err)
		os.Exit(0)
	}
	defer lock.Release()

	// Initialize service
	svc, err := NewService(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize service: %v", err)
	}

	// Start servers
	if err := svc.Start(); err != nil {
		log.Fatalf("Failed to start service: %v", err)
	}

	// Write IPC port to lock file
	lock.WritePort(cfg.IpcPort)

	if !cfg.Background {
		fmt.Printf("Flow service started (integration: %d, ipc: %d, data: %s)\n",
			cfg.IntegrationPort, cfg.IpcPort, cfg.DataDir)
	}

	// Wait for shutdown signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	if !cfg.Background {
		fmt.Println("Shutting down...")
	}
	svc.Shutdown()
}
