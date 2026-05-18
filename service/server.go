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
	integration *http.Server
	ipc        *http.Server
}

func NewServer(engine *DownloadEngine, storage *Storage, cfg *Config) *Server {
	return &Server{engine: engine, storage: storage, config: cfg}
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

	// Lifecycle
	mux.HandleFunc("POST /api/shutdown", s.handleShutdown)

	addr := fmt.Sprintf("127.0.0.1:%d", s.config.IpcPort)
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("ipc server: %w", err)
	}
	s.ipc = &http.Server{Handler: mux}
	go s.ipc.Serve(listener)
	return nil
}

func (s *Server) Stop() {
	if s.integration != nil {
		s.integration.Close()
	}
	if s.ipc != nil {
		s.ipc.Close()
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

	var ids []int64
	for _, item := range req.Items {
		id, err := s.engine.Add(item)
		if err != nil {
			writeJSON(w, CommandResponse{OK: false, Error: err.Error()})
			return
		}
		ids = append(ids, id)
		// Auto-start if not silent
		if !req.Options.SilentAdd {
			s.engine.Resume(id)
		}
	}

	// Launch UI to show the add-download overlay with the download info
	if !req.Options.SilentAdd {
		// Pass download URL to UI so it opens the add-download dialog directly
		if len(req.Items) > 0 {
			go LaunchUIWithDownload(req.Items[0].Link)
		}
	}

	writeJSON(w, AddDownloadResponse{IDs: ids})
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
