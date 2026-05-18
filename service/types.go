package main

import "time"

// DownloadStatus represents the state of a download.
type DownloadStatus string

const (
	StatusAdded       DownloadStatus = "Added"
	StatusDownloading DownloadStatus = "Downloading"
	StatusPaused      DownloadStatus = "Paused"
	StatusCompleted   DownloadStatus = "Completed"
	StatusError       DownloadStatus = "Error"
)

// DownloadItem represents a single download entry.
type DownloadItem struct {
	ID            int64          `json:"id"`
	Name          string         `json:"name"`
	URL           string         `json:"url"`
	Folder        string         `json:"folder"`
	Status        DownloadStatus `json:"status"`
	ContentLength int64          `json:"contentLength"`
	Downloaded    int64          `json:"downloaded"`
	Speed         int64          `json:"speed"`
	Parts         int            `json:"parts"`
	DateAdded     time.Time      `json:"dateAdded"`
	StartTime     *time.Time     `json:"startTime,omitempty"`
	CompleteTime  *time.Time     `json:"completeTime,omitempty"`
	Headers       map[string]string `json:"headers,omitempty"`
	Error         string         `json:"error,omitempty"`
}

// PartState tracks progress of a single download part.
type PartState struct {
	From    int64 `json:"from"`
	To      int64 `json:"to"`
	Current int64 `json:"current"`
}

func (p *PartState) IsCompleted() bool {
	return p.To > 0 && p.Current >= p.To
}

func (p *PartState) Downloaded() int64 {
	return p.Current - p.From
}

// Queue represents a download queue.
type Queue struct {
	ID       int64   `json:"id"`
	Name     string  `json:"name"`
	Items    []int64 `json:"items"`
	IsActive bool    `json:"isActive"`
}

// --- API Request/Response types ---

type AddDownloadRequest struct {
	Items   []NewDownloadItem `json:"items"`
	QueueID *int64            `json:"queueId,omitempty"`
	Options AddOptions        `json:"options"`
}

type NewDownloadItem struct {
	Type         string            `json:"type"` // "http" or "hls"
	Link         string            `json:"link"`
	Headers      map[string]string `json:"headers,omitempty"`
	DownloadPage string            `json:"downloadPage,omitempty"`
	Name         string            `json:"name,omitempty"`
	Folder       string            `json:"folder,omitempty"`
}

type AddOptions struct {
	SilentAdd   bool `json:"silentAdd"`
	SilentStart bool `json:"silentStart"`
}

type StatusResponse struct {
	Ready           bool   `json:"ready"`
	ActiveDownloads int    `json:"activeDownloads"`
	TotalDownloads  int    `json:"totalDownloads"`
	Version         string `json:"version"`
}

type CommandResponse struct {
	OK    bool   `json:"ok"`
	Error string `json:"error,omitempty"`
}

type AddDownloadResponse struct {
	IDs []int64 `json:"ids"`
}
