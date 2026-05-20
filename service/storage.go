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

// --- Categories ---

func (s *Storage) GetCategories() []*Category {
	path := filepath.Join(s.config.DataDir, "config", "download_db", "categories", "categories.json")
	data, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var categories []*Category
	if err := json.Unmarshal(data, &categories); err != nil {
		return nil
	}
	return categories
}

func (s *Storage) AddDownloadToCategory(categoryID int64, downloadID int64) error {
	categories := s.GetCategories()
	var updated bool
	for _, c := range categories {
		if c.ID == categoryID {
			found := false
			for _, itemID := range c.Items {
				if itemID == downloadID {
					found = true
					break
				}
			}
			if !found {
				c.Items = append(c.Items, downloadID)
				updated = true
			}
			break
		}
	}
	if updated {
		path := filepath.Join(s.config.DataDir, "config", "download_db", "categories", "categories.json")
		data, err := json.MarshalIndent(categories, "", "  ")
		if err != nil {
			return err
		}
		return atomicWrite(path, data)
	}
	return nil
}

func (s *Storage) AddDownloadToQueue(queueID int64, downloadID int64) error {
	queues := s.GetQueues()
	var targetQueue *Queue
	for _, q := range queues {
		if q.ID == queueID {
			targetQueue = q
			break
		}
	}
	if targetQueue != nil {
		for _, itemID := range targetQueue.Items {
			if itemID == downloadID {
				return nil
			}
		}
		targetQueue.Items = append(targetQueue.Items, downloadID)
		return s.SaveQueue(targetQueue)
	}
	return fmt.Errorf("queue not found")
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
