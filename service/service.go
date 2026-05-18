package main

// Service ties together all components.
type Service struct {
	config  *Config
	storage *Storage
	engine  *DownloadEngine
	server  *Server
}

// NewService creates and initializes the service.
func NewService(cfg *Config) (*Service, error) {
	if err := cfg.EnsureDirs(); err != nil {
		return nil, err
	}

	// Set defaults
	if cfg.DefaultParts == 0 {
		cfg.DefaultParts = 8
	}
	if cfg.MaxConcurrent == 0 {
		cfg.MaxConcurrent = 8
	}

	storage := NewStorage(cfg)
	storage.LoadConfig(cfg) // Override with persisted config

	engine := NewDownloadEngine(storage, cfg)
	if err := engine.Boot(); err != nil {
		return nil, err
	}

	server := NewServer(engine, storage, cfg)

	return &Service{
		config:  cfg,
		storage: storage,
		engine:  engine,
		server:  server,
	}, nil
}

// Start begins serving HTTP requests.
func (s *Service) Start() error {
	if err := s.server.StartIntegration(); err != nil {
		return err
	}
	if err := s.server.StartIPC(); err != nil {
		return err
	}
	return nil
}

// Shutdown gracefully stops all downloads and servers.
func (s *Service) Shutdown() {
	s.engine.StopAll()
	s.server.Stop()
}
