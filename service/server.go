package main

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"syscall"
)

// Server handles both browser extension requests and IPC from UI.
type Server struct {
	engine     *DownloadEngine
	storage    *Storage
	config     *Config
	overlay    *Overlay
	ipc        *IPCState
	integration *http.Server
	ipcServer  *http.Server
}

func NewServer(engine *DownloadEngine, storage *Storage, cfg *Config, overlay *Overlay, ipcState *IPCState) *Server {
	return &Server{engine: engine, storage: storage, config: cfg, overlay: overlay, ipc: ipcState}
}

// StartIntegration starts the browser extension HTTP server.
func (s *Server) StartIntegration() error {
	mux := http.NewServeMux()
	mux.HandleFunc("/add", s.handleAdd)
	mux.HandleFunc("/ping", s.handlePing)
	mux.HandleFunc("/queues", s.handleQueues)
	mux.HandleFunc("/", s.handleRoot)

	addr := fmt.Sprintf("127.0.0.1:%d", s.config.IntegrationPort)
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("integration server: %w", err)
	}
	s.integration = &http.Server{Handler: mux}
	go s.integration.Serve(listener)
	return nil
}

// StartIPC starts the IPC server for UI process communication.
func (s *Server) StartIPC() error {
	mux := http.NewServeMux()

	// Status
	mux.HandleFunc("GET /api/status", s.handleStatus)

	// Downloads
	mux.HandleFunc("GET /api/downloads", s.handleGetDownloads)
	mux.HandleFunc("POST /api/downloads/add", s.handleAdd)
	mux.HandleFunc("POST /api/downloads/{id}/pause", s.handlePauseDownload)
	mux.HandleFunc("POST /api/downloads/{id}/resume", s.handleResumeDownload)
	mux.HandleFunc("DELETE /api/downloads/{id}", s.handleDeleteDownload)

	// Queues
	mux.HandleFunc("GET /api/queues", s.handleQueues)

	// Config
	mux.HandleFunc("GET /api/config", s.handleGetConfig)
	mux.HandleFunc("PUT /api/config", s.handleUpdateConfig)

	// IPC lifecycle (UI connects/disconnects)
	mux.HandleFunc("POST /api/ui/connect", s.handleUIConnect)
	mux.HandleFunc("POST /api/ui/disconnect", s.handleUIDisconnect)
	mux.HandleFunc("GET /api/ui/pending", s.handleUIPending)

	// Lifecycle
	mux.HandleFunc("POST /api/shutdown", s.handleShutdown)

	addr := fmt.Sprintf("127.0.0.1:%d", s.config.IpcPort)
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("ipc server: %w", err)
	}
	s.ipcServer = &http.Server{Handler: mux}
	go s.ipcServer.Serve(listener)
	return nil
}

func (s *Server) Stop() {
	if s.integration != nil {
		s.integration.Close()
	}
	if s.ipcServer != nil {
		s.ipcServer.Close()
	}
}

// --- Handlers ---

func (s *Server) handleRoot(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, StatusResponse{Ready: true, Version: "1.0.0"})
}

func (s *Server) handlePing(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, "pong")
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, StatusResponse{
		Ready:           true,
		ActiveDownloads: s.engine.ActiveCount(),
		TotalDownloads:  len(s.engine.GetAll()),
		Version:         "1.0.0",
	})
}

func (s *Server) handleAdd(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req AddDownloadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, CommandResponse{OK: false, Error: err.Error()})
		return
	}

	if len(req.Items) == 0 {
		writeJSON(w, CommandResponse{OK: false, Error: "no items"})
		return
	}

	// If silent mode, add directly without UI
	if req.Options.SilentAdd {
		var ids []int64
		for _, item := range req.Items {
			id, err := s.engine.Add(item)
			if err != nil {
				writeJSON(w, CommandResponse{OK: false, Error: err.Error()})
				return
			}
			ids = append(ids, id)
			if !req.Options.SilentStart {
				s.engine.Resume(id)
			}
		}
		writeJSON(w, AddDownloadResponse{IDs: ids})
		return
	}

	// Non-silent: check if Flow.exe UI is connected
	item := req.Items[0]
	filename := item.Name
	if filename == "" {
		filename = filenameFromURL(item.Link)
	}

	// Sprint 3: If UI is connected, forward to it
	if s.ipc.IsConnected() {
		itemCopy := item
		if s.ipc.ForwardToUI(&itemCopy) {
			writeJSON(w, CommandResponse{OK: true})
			return
		}
	}

	// UI not connected or forward failed — show overlay
	if s.overlay != nil {
		go s.overlay.Show(item.Link, filename)
	}

	// Respond immediately to extension — overlay handles the rest
	writeJSON(w, CommandResponse{OK: true})
}

func (s *Server) handleGetDownloads(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, s.engine.GetAll())
}

func (s *Server) handlePauseDownload(w http.ResponseWriter, r *http.Request) {
	id := extractID(r)
	if id == 0 {
		writeJSON(w, CommandResponse{OK: false, Error: "invalid id"})
		return
	}
	s.engine.Pause(id)
	writeJSON(w, CommandResponse{OK: true})
}

func (s *Server) handleResumeDownload(w http.ResponseWriter, r *http.Request) {
	id := extractID(r)
	if id == 0 {
		writeJSON(w, CommandResponse{OK: false, Error: "invalid id"})
		return
	}
	s.engine.Resume(id)
	writeJSON(w, CommandResponse{OK: true})
}

func (s *Server) handleDeleteDownload(w http.ResponseWriter, r *http.Request) {
	id := extractID(r)
	if id == 0 {
		writeJSON(w, CommandResponse{OK: false, Error: "invalid id"})
		return
	}
	s.engine.Delete(id)
	writeJSON(w, CommandResponse{OK: true})
}

func (s *Server) handleQueues(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, s.storage.GetQueues())
}

func (s *Server) handleGetConfig(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, s.config)
}

func (s *Server) handleUpdateConfig(w http.ResponseWriter, r *http.Request) {
	var cfg Config
	if err := json.NewDecoder(r.Body).Decode(&cfg); err != nil {
		writeJSON(w, CommandResponse{OK: false, Error: err.Error()})
		return
	}
	s.config.MaxConcurrent = cfg.MaxConcurrent
	s.config.DefaultParts = cfg.DefaultParts
	s.config.GlobalSpeedLimit = cfg.GlobalSpeedLimit
	s.storage.SaveConfig(s.config)
	writeJSON(w, CommandResponse{OK: true})
}

func (s *Server) handleShutdown(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, CommandResponse{OK: true})
	go func() {
		// Signal main goroutine to exit
		p, _ := os.FindProcess(os.Getpid())
		p.Signal(syscall.SIGTERM)
	}()
}

// --- IPC Handlers (UI ↔ Service) ---

func (s *Server) handleUIConnect(w http.ResponseWriter, r *http.Request) {
	s.ipc.SetConnected(true)
	writeJSON(w, CommandResponse{OK: true})
}

func (s *Server) handleUIDisconnect(w http.ResponseWriter, r *http.Request) {
	s.ipc.SetConnected(false)
	writeJSON(w, CommandResponse{OK: true})
}

// handleUIPending returns any pending download items forwarded from the extension.
// The UI polls this endpoint to receive new downloads when it's connected.
func (s *Server) handleUIPending(w http.ResponseWriter, r *http.Request) {
	var items []*NewDownloadItem
	// Non-blocking drain of pending channel
	for {
		select {
		case item := <-s.ipc.PendingDownloads():
			items = append(items, item)
		default:
			goto done
		}
	}
done:
	writeJSON(w, items)
}

// --- Helpers ---

func writeJSON(w http.ResponseWriter, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func extractID(r *http.Request) int64 {
	// Try Go 1.22 path value first
	idStr := r.PathValue("id")
	if idStr == "" {
		// Fallback: parse from URL path
		parts := strings.Split(r.URL.Path, "/")
		for i, p := range parts {
			if p == "downloads" || p == "queues" {
				if i+1 < len(parts) {
					idStr = parts[i+1]
					break
				}
			}
		}
	}
	id, _ := strconv.ParseInt(idStr, 10, 64)
	return id
}
