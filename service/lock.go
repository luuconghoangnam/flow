package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// FileLock provides single-instance enforcement via file locking.
type FileLock struct {
	path string
	file *os.File
}

// AcquireLock attempts to acquire an exclusive lock.
// Returns error if another instance holds the lock.
func AcquireLock(dataDir string) (*FileLock, error) {
	os.MkdirAll(dataDir, 0755)
	lockPath := filepath.Join(dataDir, "service.lock")

	// Check for stale lock
	if isStale(lockPath) {
		os.Remove(lockPath)
	}

	file, err := os.OpenFile(lockPath, os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		return nil, fmt.Errorf("cannot open lock file: %w", err)
	}

	if err := lockFile(file); err != nil {
		file.Close()
		return nil, fmt.Errorf("lock held by another instance: %w", err)
	}

	// Write PID
	file.Truncate(0)
	file.Seek(0, 0)
	fmt.Fprintf(file, "pid=%d\nipc_port=0\n", os.Getpid())
	file.Sync()

	return &FileLock{path: lockPath, file: file}, nil
}

// WritePort updates the lock file with the IPC port.
func (l *FileLock) WritePort(port int) {
	l.file.Truncate(0)
	l.file.Seek(0, 0)
	fmt.Fprintf(l.file, "pid=%d\nipc_port=%d\n", os.Getpid(), port)
	l.file.Sync()
}

// Release releases the lock and removes the file.
func (l *FileLock) Release() {
	if l.file != nil {
		unlockFile(l.file)
		l.file.Close()
		os.Remove(l.path)
	}
}

func isStale(lockPath string) bool {
	data, err := os.ReadFile(lockPath)
	if err != nil {
		return false
	}
	for _, line := range strings.Split(string(data), "\n") {
		if strings.HasPrefix(line, "pid=") {
			pid, _ := strconv.Atoi(strings.TrimPrefix(line, "pid="))
			if pid > 0 {
				proc, err := os.FindProcess(pid)
				if err != nil {
					return true
				}
				// Best-effort check - on Windows FindProcess always succeeds
				_ = proc
				return false
			}
		}
	}
	return true
}
