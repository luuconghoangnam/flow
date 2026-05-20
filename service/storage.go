package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

// Storage handles JSON file persistence for downloads, queues, and config.
type Storage struct {
	config *Config
}

func NewStorage(cfg *Config) *Storage {
	return &Storage{config: cfg}
}

// --- Downloads ---

func (s *Storage) LoadDownloads() ([]*DownloadItem, error) {
	dir := s.config.DownloadsDir()
	entries, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}

	var items []*DownloadItem
	for _, entry := range entries {
		if filepath.Ext(entry.Name()) != ".json" {
			continue
		}
		data, err := os.ReadFile(filepath.Join(dir, entry.Name()))
		if err != nil {
			continue
		}
		var item DownloadItem
		if err := json.Unmarshal(data, &item); err != nil {
			continue
		}
		items = append(items, &item)
	}
	return items, nil
}

func (s *Storage) SaveDownload(item *DownloadItem) error {
	dir := s.config.DownloadsDir()
	os.MkdirAll(dir, 0755)
	data, err := json.MarshalIndent(item, "", "  ")
	if err != nil {
		return err
	}
	path := filepath.Join(dir, fmt.Sprintf("%d.json", item.ID))
	return atomicWrite(path, data)
}

func (s *Storage) DeleteDownload(id int64) {
	path := filepath.Join(s.config.DownloadsDir(), fmt.Sprintf("%d.json", id))
	os.Remove(path)
}

// --- Queues ---

func (s *Storage) GetQueues() []*Queue {
	dir := s.config.QueuesDir()
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	var queues []*Queue
	for _, entry := range entries {
		if filepath.Ext(entry.Name()) != ".json" {
			continue
		}
		data, err := os.ReadFile(filepath.Join(dir, entry.Name()))
		if err != nil {
			continue
		}
		var q Queue
		if err := json.Unmarshal(data, &q); err != nil {
			continue
		}
		queues = append(queues, &q)
	}
	return queues
}

func (s *Storage) SaveQueue(q *Queue) error {
	dir := s.config.QueuesDir()
	os.MkdirAll(dir, 0755)
	data, err := json.MarshalIndent(q, "", "  ")
	if err != nil {
		return err
	}
	path := filepath.Join(dir, fmt.Sprintf("%d.json", q.ID))
	return atomicWrite(path, data)
}

// --- Config ---

func (s *Storage) LoadConfig(cfg *Config) {
	data, err := os.ReadFile(cfg.ConfigFile())
	if err != nil {
		return
	}
	json.Unmarshal(data, cfg)
}

func (s *Storage) SaveConfig(cfg *Config) error {
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}
	return atomicWrite(cfg.ConfigFile(), data)
}

// --- Helpers ---

// atomicWrite writes data to a temp file then renames (atomic on most filesystems).
func atomicWrite(path string, data []byte) error {
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}
