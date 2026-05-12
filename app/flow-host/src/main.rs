use std::path::PathBuf;

use flow_core::{
    flow_clipboard_decision_path, flow_clipboard_pending_path, flow_data_dir, load_settings,
    per_host_for_url, persist_chunk_progress_worker, save_settings, set_windows_auto_start,
    should_use_manual_proxy,
    pause_active_job, resume_active_job, DownloadEngine, DownloadEvent, DownloadRepository,
    DownloadRequest, QueueJobRecord, QueueScheduler, SqliteDownloadRepository,
};
use flow_messaging::{run_native_host_loop, BrowserDownloadMessage, HostCommandHandlers};
use tokio::sync::mpsc;

fn main() {
    if handle_cli_probe() {
        return;
    }

    bootstrap_environment();

    let (engine_tx, engine_rx) = DownloadEngine::create_event_channel(2048);
    let (persist_tx, persist_rx) = mpsc::channel::<DownloadEvent>(2048);

    std::thread::spawn(move || {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("Failed to create persistence runtime");

        runtime.block_on(async move {
            tokio::spawn(async move {
                forward_events_to_persistence(engine_rx, persist_tx).await;
            });

            let _ = persist_chunk_progress_worker(flow_db_path(), persist_rx, 350).await;
        });
    });

    let download_runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
        .expect("Failed to create download runtime");
    let (queue_tx, queue_rx) = mpsc::channel::<QueueJobRecord>(1024);
    let worker_events = engine_tx.clone();
    let queue_groups = SqliteDownloadRepository::open(&flow_db_path())
        .and_then(|repo| {
            repo.init_schema()?;
            repo.list_queue_groups()
        })
        .unwrap_or_else(|_| vec![flow_core::QueueGroupRecord { id: 0, name: "Main".to_string(), max_concurrent: 3, stop_on_empty: false, active: true, schedule_json: None }]);

    download_runtime.spawn(async move {
        QueueScheduler::run_channel(flow_db_path(), queue_rx, worker_events).await;
    });

    let clipboard_queue = queue_tx.clone();
    download_runtime.spawn(async move {
        monitor_clipboard_loop(clipboard_queue).await;
    });

    let decision_queue = queue_tx.clone();
    download_runtime.spawn(async move {
        process_clipboard_decision_loop(decision_queue).await;
    });

    let recovery_queue = queue_tx.clone();
    download_runtime.block_on(async move {
        if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
            let _ = repo.init_schema();
            if let Ok(jobs) = repo.list_recoverable_jobs() {
                for job in jobs {
                    let _ = recovery_queue.send(job).await;
                }
            }
        }
    });

    let create_queue = queue_tx.clone();
    let create = move |payload: BrowserDownloadMessage| {
        let settings = load_settings(&flow_settings_path());
        if !settings.browser_integration_enabled {
            return Err("BAD_REQUEST: Browser integration is disabled in settings".to_string());
        }
        if !is_supported_download_url(&payload.url) {
            return Err("BAD_REQUEST: Only http(s) downloads are supported".to_string());
        }
        let request = map_browser_message_to_request(payload);
        let queue_tx = create_queue.clone();

        let result = download_runtime.block_on(async move { enqueue_request(request, &queue_tx).await });

        result.map(Some)
    };

    let status = move |payload: flow_messaging::DownloadStatusRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let job = repo.get_queue_job(&payload.download_id).map_err(|e| e.to_string())?;
        serde_json::to_value(job).map_err(|e| e.to_string())
    };

    let list = move |()| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let jobs = repo.list_queue_jobs().map_err(|e| e.to_string())?;
        serde_json::to_value(jobs).map_err(|e| e.to_string())
    };

    let retry_queue = queue_tx.clone();
    let retry = move |payload: flow_messaging::DownloadRetryRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        repo.reset_queue_job_for_retry(&payload.download_id).map_err(|e| e.to_string())?;
        let job = repo
            .get_queue_job(&payload.download_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| "download_id not found".to_string())?;
        let id = job.id.clone();
        retry_queue.blocking_send(job).map_err(|e| e.to_string())?;
        Ok(Some(id))
    };

    let cleanup = move |payload: flow_messaging::DownloadCleanupRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let statuses = payload.statuses.unwrap_or_else(|| vec!["Completed".to_string(), "Failed".to_string(), "Cancelled".to_string()]);
        let removed = repo.cleanup_queue_jobs_by_status(&statuses).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({ "removed": removed }))
    };

    let start_queue = queue_tx.clone();
    let start = move |payload: flow_messaging::DownloadControlRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        if resume_active_job(&payload.download_id) {
            let _ = repo.update_queue_job_status(&payload.download_id, "Downloading");
            if let Ok(Some(job)) = repo.get_queue_job(&payload.download_id) {
                let _ = repo.log_queue_event(job.queue_id, "queue_start", Some(&format!("{{\"id\":\"{}\"}}", payload.download_id)));
            }
            return Ok(Some(payload.download_id));
        }
        let mut job = repo
            .get_queue_job(&payload.download_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| "download_id not found".to_string())?;
        let id = job.id.clone();
        let _ = repo.push_queue_job_to_end(&id);
        if let Some(queue_id) = payload.queue_id {
            job.queue_id = queue_id;
            let _ = repo.upsert_queue_job(&job);
        }
        let _ = repo.set_queue_group_active(job.queue_id, true);
        let _ = repo.log_queue_event(job.queue_id, "queue_start", Some(&format!("{{\"id\":\"{}\"}}", id)));
        let _ = repo.update_queue_job_status(&id, "Queued");
        start_queue.blocking_send(job).map_err(|e| e.to_string())?;
        Ok(Some(id))
    };

    let stop = move |payload: flow_messaging::DownloadControlRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let _ = pause_active_job(&payload.download_id);
        repo.update_queue_job_status(&payload.download_id, "Paused").map_err(|e| e.to_string())?;
        if let Ok(Some(job)) = repo.get_queue_job(&payload.download_id) {
            let _ = repo.log_queue_event(job.queue_id, "queue_stop", Some(&format!("{{\"id\":\"{}\"}}", payload.download_id)));
        }
        Ok(Some(payload.download_id))
    };

    let queue_list = move |()| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        serde_json::to_value(repo.list_queue_groups().map_err(|e| e.to_string())?).map_err(|e| e.to_string())
    };

    let queue_create = move |payload: flow_messaging::QueueGroupRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let groups = repo.list_queue_groups().map_err(|e| e.to_string())?;
        let next_id = groups.iter().map(|g| g.id).max().unwrap_or(0) + 1;
        let group = flow_core::QueueGroupRecord {
            id: next_id,
            name: payload.name.unwrap_or_else(|| format!("Queue {next_id}")),
            max_concurrent: payload.max_concurrent.unwrap_or(3),
            stop_on_empty: payload.stop_on_empty.unwrap_or(false),
            active: payload.active.unwrap_or(true),
            schedule_json: None,
        };
        repo.upsert_queue_group(&group).map_err(|e| e.to_string())?;
        serde_json::to_value(group).map_err(|e| e.to_string())
    };

    let queue_update = move |payload: flow_messaging::QueueGroupRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let queue_id = payload.queue_id.ok_or_else(|| "queue_id is required".to_string())?;
        let current = repo
            .list_queue_groups()
            .map_err(|e| e.to_string())?
            .into_iter()
            .find(|g| g.id == queue_id)
            .ok_or_else(|| "queue_id not found".to_string())?;
        let group = flow_core::QueueGroupRecord {
            id: queue_id,
            name: payload.name.unwrap_or(current.name),
            max_concurrent: payload.max_concurrent.unwrap_or(current.max_concurrent),
            stop_on_empty: payload.stop_on_empty.unwrap_or(current.stop_on_empty),
            active: payload.active.unwrap_or(current.active),
            schedule_json: None,
        };
        repo.upsert_queue_group(&group).map_err(|e| e.to_string())?;
        serde_json::to_value(group).map_err(|e| e.to_string())
    };

    let queue_delete = move |payload: flow_messaging::QueueGroupRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let queue_id = payload.queue_id.ok_or_else(|| "queue_id is required".to_string())?;
        repo.delete_queue_group(queue_id).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({ "deleted": queue_id }))
    };

    let queue_move = move |payload: flow_messaging::QueueJobOrderRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let delta = match payload.direction.as_deref() {
            Some("up") => -1,
            Some("down") => 1,
            _ => return Err("BAD_REQUEST: direction must be up or down".to_string()),
        };
        repo.reorder_queue_job(&payload.download_id, delta).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({ "moved": payload.download_id }))
    };

    let queue_requeue = move |payload: flow_messaging::QueueJobOrderRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        repo.push_queue_job_to_end(&payload.download_id).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({ "requeued": payload.download_id }))
    };

    let queue_swap = move |payload: flow_messaging::QueueJobOrderRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let target_index = payload.target_index.ok_or_else(|| "BAD_REQUEST: target_index is required".to_string())?;
        repo.move_queue_job_to_index(&payload.download_id, target_index).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({ "swapped": payload.download_id, "target_index": target_index }))
    };

    let queue_events = move |payload: flow_messaging::QueueEventQueryRequest| {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let events = repo.list_recent_queue_events(payload.limit.unwrap_or(50)).map_err(|e| e.to_string())?;
        serde_json::to_value(events).map_err(|e| e.to_string())
    };

    let _ = run_native_host_loop(HostCommandHandlers { create, status, list, retry, cleanup, start, stop, queue_list, queue_create, queue_update, queue_delete, queue_move, queue_requeue, queue_swap, queue_events });
}

fn handle_cli_probe() -> bool {
    let mut args = std::env::args().skip(1);
    match args.next().as_deref() {
        Some("--health") => {
            println!("ok");
            true
        }
        Some("--version") => {
            println!("flow-host {}", env!("CARGO_PKG_VERSION"));
            true
        }
        Some("--list-jobs") => {
            print_queue_jobs(None);
            true
        }
        Some("--status") => {
            print_queue_jobs(args.next());
            true
        }
        Some("--queue-events") => {
            let limit = args.next().and_then(|v| v.parse::<usize>().ok()).unwrap_or(50);
            print_queue_events(limit);
            true
        }
        _ => false,
    }
}

fn print_queue_jobs(download_id: Option<String>) {
    let result = (|| -> Result<serde_json::Value, String> {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        match download_id {
            Some(id) => serde_json::to_value(repo.get_queue_job(&id).map_err(|e| e.to_string())?)
                .map_err(|e| e.to_string()),
            None => serde_json::to_value(repo.list_queue_jobs().map_err(|e| e.to_string())?)
                .map_err(|e| e.to_string()),
        }
    })();

    match result {
        Ok(value) => println!("{}", serde_json::to_string_pretty(&value).unwrap_or_else(|_| "null".to_string())),
        Err(error) => {
            eprintln!("{error}");
            std::process::exit(1);
        }
    }
}

fn print_queue_events(limit: usize) {
    let result = (|| -> Result<serde_json::Value, String> {
        let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
        repo.init_schema().map_err(|e| e.to_string())?;
        let events = repo.list_recent_queue_events(limit).map_err(|e| e.to_string())?;
        serde_json::to_value(events).map_err(|e| e.to_string())
    })();

    match result {
        Ok(value) => println!("{}", serde_json::to_string_pretty(&value).unwrap_or_else(|_| "[]".to_string())),
        Err(error) => {
            eprintln!("{error}");
            std::process::exit(1);
        }
    }
}

fn flow_db_path() -> PathBuf {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")));
    let dir = base.join("Flow");
    let _ = std::fs::create_dir_all(&dir);
    dir.join("flow.db")
}

fn flow_settings_path() -> PathBuf {
    flow_db_path().with_file_name("settings.json")
}

fn is_supported_download_url(url: &str) -> bool {
    url.starts_with("http://") || url.starts_with("https://")
}

fn current_queue_order() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|v| v.as_millis() as i64)
        .unwrap_or_default()
}

fn bootstrap_environment() {
    let settings_path = flow_settings_path();
    let settings = load_settings(&settings_path);
    let _ = save_settings(&settings_path, &settings);
    if settings.auto_start {
        if let Ok(exe) = std::env::current_exe() {
            let _ = set_windows_auto_start("FlowHost", &exe.to_string_lossy(), true);
        }
    }
    let _ = std::fs::create_dir_all(flow_data_dir());
}

async fn monitor_clipboard_loop(_queue_tx: mpsc::Sender<QueueJobRecord>) {
    let mut clipboard = match arboard::Clipboard::new() {
        Ok(value) => value,
        Err(_) => return,
    };
    let mut last_seen = String::new();
    loop {
        let settings = load_settings(&flow_settings_path());
        if settings.clipboard_monitoring {
            if let Ok(text) = clipboard.get_text() {
                if text != last_seen && is_supported_download_url(&text) {
                    last_seen = text.clone();
                    let request = map_browser_message_to_request(BrowserDownloadMessage {
                        url: text,
                        file_name: None,
                        output_dir: settings.default_download_folder.clone(),
                        connections: Some(settings.thread_count.max(1)),
                        headers: None,
                        referrer: None,
                        cookies: None,
                        user_agent: None,
                        username: None,
                        password: None,
                        priority: Some(0),
                        queue_id: Some(0),
                        expected_sha256_hex: None,
                    });
                    let pending = serde_json::json!({
                        "url": request.url,
                        "file_name": request.file_name,
                        "output_dir": request.output_dir,
                        "connections": request.connections,
                        "priority": request.priority,
                        "queue_id": request.queue_id,
                    });
                    let _ = std::fs::write(flow_clipboard_pending_path(), pending.to_string());
                }
            }
        }
        tokio::time::sleep(std::time::Duration::from_millis(1200)).await;
    }
}

async fn process_clipboard_decision_loop(queue_tx: mpsc::Sender<QueueJobRecord>) {
    loop {
        let decision_path = flow_clipboard_decision_path();
        if let Ok(text) = std::fs::read_to_string(&decision_path) {
            if let Ok(value) = serde_json::from_str::<serde_json::Value>(&text) {
                let action = value.get("action").and_then(|v| v.as_str()).unwrap_or_default();
                if action == "queue" {
                    if let Some(url) = value.get("url").and_then(|v| v.as_str()) {
                        let request = map_browser_message_to_request(BrowserDownloadMessage {
                            url: url.to_string(),
                            file_name: value.get("file_name").and_then(|v| v.as_str()).map(|s| s.to_string()),
                            output_dir: value.get("output_dir").and_then(|v| v.as_str()).map(|s| s.to_string()),
                            connections: value.get("connections").and_then(|v| v.as_u64()).map(|n| n as usize),
                            headers: None,
                            referrer: None,
                            cookies: None,
                            user_agent: None,
                            username: None,
                            password: None,
                            priority: value.get("priority").and_then(|v| v.as_i64()),
                            queue_id: value.get("queue_id").and_then(|v| v.as_i64()),
                            expected_sha256_hex: None,
                        });
                        let _ = enqueue_request(request, &queue_tx).await;
                    }
                }
                let _ = std::fs::remove_file(&decision_path);
                let _ = std::fs::remove_file(flow_clipboard_pending_path());
            }
        }
        tokio::time::sleep(std::time::Duration::from_millis(800)).await;
    }
}

async fn enqueue_request(request: DownloadRequest, queue_tx: &mpsc::Sender<QueueJobRecord>) -> Result<String, String> {
    let repo = SqliteDownloadRepository::open(&flow_db_path()).map_err(|e| e.to_string())?;
    repo.init_schema().map_err(|e| e.to_string())?;
    let queue_id = if repo.get_queue_group(request.queue_id).map_err(|e| e.to_string())?.is_some() {
        request.queue_id
    } else {
        0
    };
    let preview = DownloadEngine::new().enqueue(request.clone()).await;
    let job = QueueJobRecord {
        id: preview.id.0.clone(),
        queue_id,
        url: request.url,
        output_dir: request.output_dir.to_string_lossy().to_string(),
        file_name: request.file_name,
        connections: request.connections,
        expected_sha256_hex: request.expected_sha256_hex,
        headers_json: serde_json::to_string(&request.headers).ok(),
        referrer: request.referrer,
        cookies: request.cookies,
        user_agent: request.user_agent,
        username: request.username,
        password: request.password,
        proxy_url: request.proxy_url,
        proxy_username: request.proxy_username,
        proxy_password: request.proxy_password,
        status: "Queued".to_string(),
        priority: request.priority,
        queue_order: current_queue_order(),
        attempt_count: 0,
        last_error: None,
    };
    repo.upsert_queue_job(&job).map_err(|e| e.to_string())?;
    let download_id = job.id.clone();
    queue_tx.send(job).await.map_err(|e| e.to_string())?;
    Ok(download_id)
}

fn map_browser_message_to_request(payload: BrowserDownloadMessage) -> DownloadRequest {
    let file_name = payload
        .file_name
        .filter(|name| !name.trim().is_empty())
        .unwrap_or_else(|| file_name_from_url(&payload.url));
    let settings = load_settings(&flow_settings_path());
    let per_host = per_host_for_url(&settings, &payload.url);
    let proxy = if should_use_manual_proxy(&settings.proxy, &payload.url) {
        Some(settings.proxy.manual.clone())
    } else {
        None
    };

    DownloadRequest {
        url: payload.url,
        output_dir: PathBuf::from(payload.output_dir.unwrap_or_else(default_download_dir)),
        file_name,
        connections: per_host.and_then(|v| v.thread_count).or(payload.connections).unwrap_or(8).max(1),
        expected_sha256_hex: payload.expected_sha256_hex,
        headers: payload.headers.unwrap_or_default(),
        referrer: payload.referrer,
        cookies: payload.cookies,
        user_agent: per_host.and_then(|v| v.user_agent.clone()).or(payload.user_agent),
        username: per_host.and_then(|v| v.username.clone()).or(payload.username),
        password: per_host.and_then(|v| v.password.clone()).or(payload.password),
        proxy_url: proxy.as_ref().map(|proxy| format!("{}://{}:{}", proxy.scheme, proxy.host, proxy.port)),
        proxy_username: proxy.as_ref().and_then(|proxy| proxy.username.clone()),
        proxy_password: proxy.as_ref().and_then(|proxy| proxy.password.clone()),
        priority: payload.priority.unwrap_or(0),
        queue_id: payload.queue_id.unwrap_or(0),
        speed_limit_bps: settings.global_speed_limit_bps,
    }
}

fn file_name_from_url(url: &str) -> String {
    let candidate = url
        .split('?')
        .next()
        .unwrap_or(url)
        .trim_end_matches('/')
        .rsplit('/')
        .next()
        .unwrap_or("download.bin");
    let sanitized: String = candidate
        .chars()
        .map(|ch| match ch {
            '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' => '_',
            _ => ch,
        })
        .collect();
    if sanitized.trim().is_empty() {
        "download.bin".to_string()
    } else {
        sanitized
    }
}

fn default_download_dir() -> String {
    std::env::var("USERPROFILE")
        .map(|home| PathBuf::from(home).join("Downloads").to_string_lossy().to_string())
        .unwrap_or_else(|_| "downloads".to_string())
}

async fn forward_events_to_persistence(
    mut source: mpsc::Receiver<DownloadEvent>,
    target: mpsc::Sender<DownloadEvent>,
) {
    while let Some(event) = source.recv().await {
        let _ = target.send(event).await;
    }
}
