package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"
)

// DownloadEngine manages all active and pending downloads.
type DownloadEngine struct {
	mu       sync.RWMutex
	items    map[int64]*DownloadItem
	active   map[int64]context.CancelFunc
	nextID   int64
	storage  *Storage
	config   *Config
	client   *http.Client
}

func NewDownloadEngine(storage *Storage, cfg *Config) *DownloadEngine {
	return &DownloadEngine{
		items:   make(map[int64]*DownloadItem),
		active:  make(map[int64]context.CancelFunc),
		storage: storage,
		config:  cfg,
		client: &http.Client{
			Timeout: 0, // No timeout for downloads
			Transport: &http.Transport{
				MaxIdleConns:        10,
				MaxConnsPerHost:     10,
				IdleConnTimeout:     30 * time.Second,
				DisableCompression:  true,
			},
		},
	}
}

// Boot loads persisted downloads from disk.
func (e *DownloadEngine) Boot() error {
	items, err := e.storage.LoadDownloads()
	if err != nil {
		return err
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	for _, item := range items {
		e.items[item.ID] = item
		if item.ID >= e.nextID {
			e.nextID = item.ID + 1
		}
	}
	return nil
}

// Add creates a new download entry.
func (e *DownloadEngine) Add(req NewDownloadItem) (int64, error) {
	e.mu.Lock()
	defer e.mu.Unlock()

	id := e.nextID
	e.nextID++

	name := req.Name
	if name == "" {
		name = filenameFromURL(req.Link)
	}
	folder := req.Folder
	if folder == "" {
		home, _ := os.UserHomeDir()
		folder = filepath.Join(home, "Downloads")
	}

	item := &DownloadItem{
		ID:        id,
		Name:      name,
		URL:       req.Link,
		Folder:    folder,
		Status:    StatusAdded,
		Headers:   req.Headers,
		DateAdded: time.Now(),
		Parts:     e.config.DefaultParts,
	}
	if item.Parts == 0 {
		item.Parts = 8
	}

	e.items[id] = item
	e.storage.SaveDownload(item)
	return id, nil
}

// Resume starts or resumes a download.
func (e *DownloadEngine) Resume(id int64) {
	e.mu.Lock()
	item, ok := e.items[id]
	if !ok || item.Status == StatusCompleted {
		e.mu.Unlock()
		return
	}
	if _, active := e.active[id]; active {
		e.mu.Unlock()
		return
	}

	ctx, cancel := context.WithCancel(context.Background())
	e.active[id] = cancel
	item.Status = StatusDownloading
	now := time.Now()
	item.StartTime = &now
	e.mu.Unlock()

	go e.download(ctx, item)
}

// Pause stops an active download.
func (e *DownloadEngine) Pause(id int64) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if cancel, ok := e.active[id]; ok {
		cancel()
		delete(e.active, id)
	}
	if item, ok := e.items[id]; ok {
		item.Status = StatusPaused
		e.storage.SaveDownload(item)
	}
}

// Delete removes a download and its file.
func (e *DownloadEngine) Delete(id int64) {
	e.Pause(id)
	e.mu.Lock()
	defer e.mu.Unlock()
	if item, ok := e.items[id]; ok {
		os.Remove(filepath.Join(item.Folder, item.Name))
		delete(e.items, id)
		e.storage.DeleteDownload(id)
	}
}

// GetAll returns all download items.
func (e *DownloadEngine) GetAll() []*DownloadItem {
	e.mu.RLock()
	defer e.mu.RUnlock()
	result := make([]*DownloadItem, 0, len(e.items))
	for _, item := range e.items {
		result = append(result, item)
	}
	return result
}

// ActiveCount returns number of currently downloading items.
func (e *DownloadEngine) ActiveCount() int {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return len(e.active)
}

// StopAll pauses all active downloads.
func (e *DownloadEngine) StopAll() {
	e.mu.Lock()
	defer e.mu.Unlock()
	for id, cancel := range e.active {
		cancel()
		if item, ok := e.items[id]; ok {
			item.Status = StatusPaused
			e.storage.SaveDownload(item)
		}
	}
	e.active = make(map[int64]context.CancelFunc)
}

// download performs the actual HTTP download with multi-part support.
func (e *DownloadEngine) download(ctx context.Context, item *DownloadItem) {
	defer func() {
		e.mu.Lock()
		delete(e.active, item.ID)
		e.mu.Unlock()
	}()

	// Probe file size and resume support
	size, supportsRange, err := e.probe(item)
	if err != nil {
		item.Status = StatusError
		item.Error = err.Error()
		e.storage.SaveDownload(item)
		return
	}
	item.ContentLength = size

	// Create output file
	outPath := filepath.Join(item.Folder, item.Name)
	os.MkdirAll(item.Folder, 0755)

	if supportsRange && size > 0 && item.Parts > 1 {
		err = e.multiPartDownload(ctx, item, outPath, size)
	} else {
		err = e.singlePartDownload(ctx, item, outPath)
	}

	if err != nil {
		if ctx.Err() != nil {
			// Cancelled by user (pause)
			return
		}
		item.Status = StatusError
		item.Error = err.Error()
	} else {
		item.Status = StatusCompleted
		now := time.Now()
		item.CompleteTime = &now
		item.Downloaded = item.ContentLength
	}
	e.storage.SaveDownload(item)
}

// probe checks file size and range support.
func (e *DownloadEngine) probe(item *DownloadItem) (int64, bool, error) {
	req, _ := http.NewRequest("GET", item.URL, nil)
	req.Header.Set("Range", "bytes=0-0")
	for k, v := range item.Headers {
		req.Header.Set(k, v)
	}

	resp, err := e.client.Do(req)
	if err != nil {
		return 0, false, err
	}
	resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		return 0, false, fmt.Errorf("server returned status: %s", resp.Status)
	}

	if resp.StatusCode == 206 {
		// Supports range
		size := resp.ContentLength
		if cr := resp.Header.Get("Content-Range"); cr != "" {
			// Parse "bytes 0-0/12345"
			fmt.Sscanf(cr, "bytes 0-0/%d", &size)
		}
		return size, true, nil
	}
	return resp.ContentLength, false, nil
}

// singlePartDownload downloads without range support.
func (e *DownloadEngine) singlePartDownload(ctx context.Context, item *DownloadItem, outPath string) error {
	req, _ := http.NewRequestWithContext(ctx, "GET", item.URL, nil)
	for k, v := range item.Headers {
		req.Header.Set(k, v)
	}

	resp, err := e.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("server returned status: %s", resp.Status)
	}

	f, err := os.Create(outPath)
	if err != nil {
		return err
	}
	defer f.Close()

	var downloaded int64
	buf := make([]byte, 32*1024)
	for {
		n, readErr := resp.Body.Read(buf)
		if n > 0 {
			f.Write(buf[:n])
			downloaded += int64(n)
			atomic.StoreInt64(&item.Downloaded, downloaded)
		}
		if readErr != nil {
			if readErr == io.EOF {
				return nil
			}
			return readErr
		}
	}
}

// multiPartDownload downloads using multiple concurrent connections.
func (e *DownloadEngine) multiPartDownload(ctx context.Context, item *DownloadItem, outPath string, totalSize int64) error {
	// Create file with full size
	f, err := os.Create(outPath)
	if err != nil {
		return err
	}
	f.Truncate(totalSize)
	f.Close()

	partSize := totalSize / int64(item.Parts)
	var wg sync.WaitGroup
	errCh := make(chan error, item.Parts)

	for i := 0; i < item.Parts; i++ {
		from := int64(i) * partSize
		to := from + partSize - 1
		if i == item.Parts-1 {
			to = totalSize - 1
		}

		wg.Add(1)
		go func(partFrom, partTo int64) {
			defer wg.Done()
			if err := e.downloadPart(ctx, item, outPath, partFrom, partTo); err != nil {
				errCh <- err
			}
		}(from, to)
	}

	wg.Wait()
	close(errCh)

	if err := <-errCh; err != nil {
		return err
	}
	return nil
}

// downloadPart downloads a single byte range and writes to the correct file offset.
func (e *DownloadEngine) downloadPart(ctx context.Context, item *DownloadItem, outPath string, from, to int64) error {
	req, _ := http.NewRequestWithContext(ctx, "GET", item.URL, nil)
	req.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", from, to))
	for k, v := range item.Headers {
		req.Header.Set(k, v)
	}

	resp, err := e.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusPartialContent {
		return fmt.Errorf("server returned status: %s instead of 206 Partial Content", resp.Status)
	}

	f, err := os.OpenFile(outPath, os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()

	f.Seek(from, io.SeekStart)
	buf := make([]byte, 32*1024)
	for {
		n, readErr := resp.Body.Read(buf)
		if n > 0 {
			f.WriteAt(buf[:n], from)
			from += int64(n)
			atomic.AddInt64(&item.Downloaded, int64(n))
		}
		if readErr != nil {
			if readErr == io.EOF {
				return nil
			}
			return readErr
		}
	}
}

func filenameFromURL(url string) string {
	parts := filepath.Base(url)
	if idx := len(parts) - 1; idx > 0 {
		// Remove query params
		for i, c := range parts {
			if c == '?' || c == '#' {
				parts = parts[:i]
				break
			}
		}
	}
	if parts == "" || parts == "/" || parts == "." {
		return "download"
	}
	return parts
}
