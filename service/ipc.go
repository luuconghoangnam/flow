package main

import (
	"sync"
)

// IPCState tracks whether the full UI (Flow.exe) is connected.
// When connected, extension requests are forwarded to the UI
// instead of showing the lightweight WebView overlay.
type IPCState struct {
	mu        sync.RWMutex
	connected bool
	// Channel to send download requests to the connected UI
	pendingCh chan *NewDownloadItem
}

func NewIPCState() *IPCState {
	return &IPCState{
		pendingCh: make(chan *NewDownloadItem, 16),
	}
}

// SetConnected marks the UI as connected/disconnected.
func (s *IPCState) SetConnected(connected bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.connected = connected
	// Drain pending channel on disconnect
	if !connected {
		for {
			select {
			case <-s.pendingCh:
			default:
				return
			}
		}
	}
}

// IsConnected returns whether the UI is currently connected.
func (s *IPCState) IsConnected() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.connected
}

// ForwardToUI sends a download item to the connected UI.
// Returns true if the UI is connected and the item was queued.
func (s *IPCState) ForwardToUI(item *NewDownloadItem) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if !s.connected {
		return false
	}
	select {
	case s.pendingCh <- item:
		return true
	default:
		// Channel full, UI might be stuck
		return false
	}
}

// PendingDownloads returns the channel for the UI to consume forwarded downloads.
func (s *IPCState) PendingDownloads() <-chan *NewDownloadItem {
	return s.pendingCh
}
