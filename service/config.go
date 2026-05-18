package main

import (
	"os"
	"path/filepath"
	"runtime"
)

// Config holds all service configuration.
type Config struct {
	IntegrationPort int    `json:"integrationPort"`
	IpcPort         int    `json:"ipcPort"`
	DataDir         string `json:"dataDir"`
	Background      bool   `json:"-"`

	// Download settings
	MaxConcurrent   int   `json:"maxConcurrent"`
	DefaultParts    int   `json:"defaultParts"`
	GlobalSpeedLimit int64 `json:"globalSpeedLimit"` // bytes/sec, 0 = unlimited
}

func defaultDataDir() string {
	switch runtime.GOOS {
	case "windows":
		appData := os.Getenv("APPDATA")
		if appData == "" {
			appData = filepath.Join(os.Getenv("USERPROFILE"), "AppData", "Roaming")
		}
		return filepath.Join(appData, "Flow")
	case "darwin":
		home, _ := os.UserHomeDir()
		return filepath.Join(home, "Library", "Application Support", "Flow")
	default:
		home, _ := os.UserHomeDir()
		return filepath.Join(home, ".local", "share", "Flow")
	}
}

func (c *Config) DownloadsDir() string { return filepath.Join(c.DataDir, "downloads") }
func (c *Config) PartsDir() string     { return filepath.Join(c.DataDir, "parts") }
func (c *Config) QueuesDir() string    { return filepath.Join(c.DataDir, "queues") }
func (c *Config) ConfigFile() string   { return filepath.Join(c.DataDir, "config.json") }

func (c *Config) EnsureDirs() error {
	dirs := []string{c.DataDir, c.DownloadsDir(), c.PartsDir(), c.QueuesDir()}
	for _, d := range dirs {
		if err := os.MkdirAll(d, 0755); err != nil {
			return err
		}
	}
	return nil
}
