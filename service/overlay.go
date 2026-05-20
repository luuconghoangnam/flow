package main

import (
	"embed"
	"fmt"
	neturl "net/url"
	"os"
	"path/filepath"
	"sync"

	webview "github.com/jchv/go-webview-selector"
)

//go:embed ui/add_download.html ui/style.css
var uiFS embed.FS

// Overlay manages the WebView add-download dialog.
// Only one overlay can be open at a time.
type Overlay struct {
	mu      sync.Mutex
	active  bool
	wv      webview.WebView
	engine  *DownloadEngine
	storage *Storage
	config  *Config
}

func NewOverlay(engine *DownloadEngine, storage *Storage, cfg *Config) *Overlay {
	return &Overlay{
		engine:  engine,
		storage: storage,
		config:  cfg,
	}
}

// Show opens the add-download overlay with the given URL and filename.
// If an overlay is already open, it brings it to focus and updates the content.
// This must be called from any goroutine — it dispatches to the UI thread internally.
func (o *Overlay) Show(url, filename string) {
	o.mu.Lock()
	if o.active && o.wv != nil {
		// Already open — update content
		wv := o.wv
		o.mu.Unlock()
		wv.Dispatch(func() {
			folder := defaultDownloadFolder()
			js := fmt.Sprintf(`setDownloadInfo(%q, %q, %q, 0)`, url, filename, folder)
			wv.Eval(js)
		})
		return
	}
	o.active = true
	o.mu.Unlock()

	// Run overlay in a new goroutine (WebView.Run blocks)
	go o.run(url, filename)
}

func (o *Overlay) run(url, filename string) {
	defer func() {
		o.mu.Lock()
		o.active = false
		o.wv = nil
		o.mu.Unlock()
	}()

	wv := webview.New(false)
	if wv == nil {
		fmt.Fprintf(os.Stderr, "overlay: failed to create webview\n")
		return
	}
	defer wv.Destroy()

	o.mu.Lock()
	o.wv = wv
	o.mu.Unlock()

	wv.SetTitle("Add Download — Flow")
	wv.SetSize(500, 320, webview.HintNone)

	// Bind Go functions to JS
	wv.Bind("flowDownload", func(dlURL, name, folder string, categoryID float64, queueID float64, startNow bool) {
		o.startDownload(dlURL, name, folder, int64(categoryID), int64(queueID), startNow)
		wv.Terminate()
	})

	wv.Bind("flowGetCategories", func() []*Category {
		return o.storage.GetCategories()
	})

	wv.Bind("flowGetQueues", func() []*Queue {
		return o.storage.GetQueues()
	})

	wv.Bind("flowBrowse", func() string {
		return o.browseFolder()
	})

	wv.Bind("flowCancel", func() {
		wv.Terminate()
	})

	// Load HTML with embedded CSS
	html := o.buildHTML()
	// Use Navigate with data URI since SetHtml may not be available in all versions
	dataURI := "data:text/html;charset=utf-8," + neturl.PathEscape(html)
	wv.Navigate(dataURI)

	// Set initial download info
	folder := defaultDownloadFolder()
	initJS := fmt.Sprintf(`setDownloadInfo(%q, %q, %q, 0)`, url, filename, folder)
	wv.Init(initJS)

	wv.Run()
}

// IsActive returns whether the overlay is currently showing.
func (o *Overlay) IsActive() bool {
	o.mu.Lock()
	defer o.mu.Unlock()
	return o.active
}

// Close terminates the overlay if open.
func (o *Overlay) Close() {
	o.mu.Lock()
	if o.active && o.wv != nil {
		o.wv.Terminate()
	}
	o.mu.Unlock()
}

func (o *Overlay) startDownload(url, name, folder string, categoryID int64, queueID int64, startNow bool) {
	item := NewDownloadItem{
		Type:   "http",
		Link:   url,
		Name:   name,
		Folder: folder,
	}
	id, err := o.engine.Add(item)
	if err != nil {
		fmt.Fprintf(os.Stderr, "overlay: failed to add download: %v\n", err)
		return
	}
	if categoryID != 0 {
		o.storage.AddDownloadToCategory(categoryID, id)
	}
	if queueID != 0 {
		o.storage.AddDownloadToQueue(queueID, id)
	}
	if startNow {
		o.engine.Resume(id)
	}
}

func (o *Overlay) browseFolder() string {
	// Platform-specific folder dialog — implemented in overlay_windows.go / overlay_other.go
	return showFolderDialog()
}

func (o *Overlay) buildHTML() string {
	htmlData, err := uiFS.ReadFile("ui/add_download.html")
	if err != nil {
		return "<html><body><p>Error loading UI</p></body></html>"
	}
	cssData, err := uiFS.ReadFile("ui/style.css")
	if err != nil {
		cssData = []byte("")
	}

	// Inline the CSS into the HTML (replace the <link> tag)
	html := string(htmlData)
	// Replace the stylesheet link with inline style
	styleTag := fmt.Sprintf("<style>%s</style>", string(cssData))
	// Simple replacement of the link tag
	html = replaceStyleLink(html, styleTag)
	return html
}

func replaceStyleLink(html, styleTag string) string {
	// Find and replace <link rel="stylesheet" href="style.css">
	const linkTag = `<link rel="stylesheet" href="style.css">`
	for i := 0; i < len(html)-len(linkTag); i++ {
		if html[i:i+len(linkTag)] == linkTag {
			return html[:i] + styleTag + html[i+len(linkTag):]
		}
	}
	// Fallback: insert before </head>
	const headClose = "</head>"
	for i := 0; i < len(html)-len(headClose); i++ {
		if html[i:i+len(headClose)] == headClose {
			return html[:i] + styleTag + html[i:]
		}
	}
	return styleTag + html
}

func defaultDownloadFolder() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return "."
	}
	dl := filepath.Join(home, "Downloads")
	if _, err := os.Stat(dl); err == nil {
		return dl
	}
	return home
}
