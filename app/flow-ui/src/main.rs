slint::include_modules!();

use std::sync::mpsc::channel;
use std::sync::{Arc, Mutex};

use flow_core::{flow_clipboard_decision_path, flow_clipboard_pending_path, flow_db_path, flow_signal_path, flow_data_dir, is_windows_auto_start_enabled, load_settings, pause_active_job, save_settings, set_windows_auto_start, DownloadRepository, SqliteDownloadRepository};
use notify::{RecommendedWatcher, RecursiveMode, Watcher};
use slint::{ModelRc, SharedString, VecModel};
use tray_icon::menu::{Menu, MenuItem};
use tray_icon::TrayIconBuilder;

struct TrayContext {
    _tray_icon: tray_icon::TrayIcon,
    _timer: slint::Timer,
}

struct QueueUiState {
    queue_labels: Vec<SharedString>,
    queue_ids: Vec<i64>,
    row_labels: Vec<SharedString>,
    row_ids: Vec<String>,
    queue_summary: String,
}

fn main() {
    flow_core::init();
    let app = MainWindow::new().expect("Failed to create main window");
    let selected_queue = Arc::new(Mutex::new(0_i64));
    let selected_download = Arc::new(Mutex::new(None::<String>));
    let queue_state = load_queue_ui_state(*selected_queue.lock().expect("selected queue lock"));
    app.set_queue_groups(ModelRc::new(VecModel::from(queue_state.queue_labels)));
    app.set_download_items(ModelRc::new(VecModel::from(queue_state.row_labels)));
    app.set_queue_config_summary(queue_state.queue_summary.into());
    let (appearance, engine, browser) = load_settings_sections();
    app.set_appearance_summary(appearance.into());
    app.set_engine_summary(engine.into());
    app.set_browser_summary(browser.into());
    app.set_clipboard_pending_summary(load_clipboard_pending_summary().into());
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    app.set_proxy_host_text(settings.proxy.manual.host.into());
    app.set_perhost_first_text(settings.per_host.first().map(|v| v.host.clone()).unwrap_or_default().into());

    let _tray_context = setup_tray_if_enabled(&app);

    app.on_retry_failed(|| {
        let db_path = flow_db_path();
        if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
            let _ = repo.init_schema();
            if let Ok(rows) = repo.list_queue_view_rows() {
                for row in rows {
                    if row.status == "Failed" {
                        let _ = repo.reset_queue_job_for_retry(&row.id);
                    }
                }
            }
        }
    });

    app.on_cleanup_finished(|| {
        let db_path = flow_db_path();
        if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
            let _ = repo.init_schema();
            let statuses = vec!["Completed".to_string(), "Failed".to_string(), "Cancelled".to_string()];
            let _ = repo.cleanup_queue_jobs_by_status(&statuses);
        }
    });

    app.on_toggle_auto_start(|| mutate_settings(|settings| {
        settings.auto_start = !settings.auto_start;
        if let Ok(exe) = std::env::current_exe() {
            let _ = set_windows_auto_start("FlowUI", &exe.to_string_lossy(), settings.auto_start);
        }
    }));
    app.on_toggle_system_tray(|| mutate_settings(|settings| settings.use_system_tray = !settings.use_system_tray));
    app.on_toggle_browser_integration(|| mutate_settings(|settings| settings.browser_integration_enabled = !settings.browser_integration_enabled));
    app.on_toggle_clipboard_monitoring(|| mutate_settings(|settings| settings.clipboard_monitoring = !settings.clipboard_monitoring));
    app.on_settings_thread_minus(|| mutate_settings(|settings| settings.thread_count = settings.thread_count.saturating_sub(1).max(1)));
    app.on_settings_thread_plus(|| mutate_settings(|settings| settings.thread_count += 1));
    app.on_settings_max_minus(|| mutate_settings(|settings| settings.max_concurrent_downloads = settings.max_concurrent_downloads.saturating_sub(1).max(1)));
    app.on_settings_max_plus(|| mutate_settings(|settings| settings.max_concurrent_downloads += 1));
    app.on_settings_use_downloads_folder(|| mutate_settings(|settings| settings.default_download_folder = Some(default_downloads_folder())));
    app.on_toggle_proxy_mode(|| mutate_settings(|settings| {
        settings.proxy.mode = match settings.proxy.mode {
            flow_core::ProxyMode::Direct => flow_core::ProxyMode::Manual,
            flow_core::ProxyMode::Manual => flow_core::ProxyMode::System,
            flow_core::ProxyMode::System => flow_core::ProxyMode::Direct,
        };
    }));
    app.on_proxy_port_minus(|| mutate_settings(|settings| settings.proxy.manual.port = settings.proxy.manual.port.saturating_sub(1).max(1)));
    app.on_proxy_port_plus(|| mutate_settings(|settings| settings.proxy.manual.port = settings.proxy.manual.port.saturating_add(1)));
    app.on_proxy_set_localhost(|| mutate_settings(|settings| settings.proxy.manual.host = "127.0.0.1".to_string()));
    app.on_proxy_set_auth_sample(|| mutate_settings(|settings| {
        settings.proxy.manual.username = Some("proxy-user".to_string());
        settings.proxy.manual.password = Some("proxy-pass".to_string());
    }));
    app.on_proxy_clear_auth(|| mutate_settings(|settings| {
        settings.proxy.manual.username = None;
        settings.proxy.manual.password = None;
    }));
    app.on_perhost_add_sample(|| mutate_settings(|settings| {
        if !settings.per_host.iter().any(|item| item.host == "*.example.com") {
            settings.per_host.push(flow_core::PerHostSettings {
                host: "*.example.com".to_string(),
                username: None,
                password: None,
                user_agent: Some("Flow Custom UA".to_string()),
                thread_count: Some(4),
            });
        }
    }));
    app.on_perhost_remove_last(|| mutate_settings(|settings| {
        let _ = settings.per_host.pop();
    }));
    app.on_perhost_clear(|| mutate_settings(|settings| settings.per_host.clear()));
    app.on_proxy_host_changed(|value| mutate_settings(|settings| settings.proxy.manual.host = value.to_string()));
    app.on_perhost_first_changed(|value| {
        mutate_settings(|settings| {
            if let Some(first) = settings.per_host.first_mut() {
                first.host = value.to_string();
            } else if !value.trim().is_empty() {
                settings.per_host.push(flow_core::PerHostSettings {
                    host: value.to_string(),
                    username: None,
                    password: None,
                    user_agent: None,
                    thread_count: None,
                });
            }
        })
    });
    app.on_clipboard_queue(|| {
        if let Some(value) = load_clipboard_pending_json() {
            let mut decision = serde_json::Map::new();
            decision.insert("action".to_string(), serde_json::Value::String("queue".to_string()));
            if let Some(url) = value.get("url") {
                decision.insert("url".to_string(), url.clone());
            }
            if let Some(file_name) = value.get("file_name") {
                decision.insert("file_name".to_string(), file_name.clone());
            }
            if let Some(output_dir) = value.get("output_dir") {
                decision.insert("output_dir".to_string(), output_dir.clone());
            }
            if let Some(connections) = value.get("connections") {
                decision.insert("connections".to_string(), connections.clone());
            }
            if let Some(priority) = value.get("priority") {
                decision.insert("priority".to_string(), priority.clone());
            }
            if let Some(queue_id) = value.get("queue_id") {
                decision.insert("queue_id".to_string(), queue_id.clone());
            }
            let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
        }
    });
    app.on_clipboard_dismiss(|| {
        let mut decision = serde_json::Map::new();
        decision.insert("action".to_string(), serde_json::Value::String("dismiss".to_string()));
        let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
    });

    {
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        app.on_create_queue(move || {
            let db_path = flow_db_path();
            if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
                let _ = repo.init_schema();
                let groups = repo.list_queue_groups().unwrap_or_default();
                let next_id = groups.iter().map(|g| g.id).max().unwrap_or(0) + 1;
                let group = flow_core::QueueGroupRecord {
                    id: next_id,
                    name: format!("Queue {next_id}"),
                    max_concurrent: 3,
                    stop_on_empty: false,
                    active: true,
                };
                let _ = repo.upsert_queue_group(&group);
                if let Ok(mut selected) = selected_queue.lock() {
                    *selected = next_id;
                }
                if let Ok(mut selected) = selected_download.lock() {
                    *selected = None;
                }
                let state = load_queue_ui_state(next_id);
                let weak2 = weak.clone();
                let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
                    app.set_download_items(ModelRc::new(VecModel::from(state.row_labels)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    let (appearance, engine, browser) = load_settings_sections();
                    app.set_appearance_summary(appearance.into());
                    app.set_engine_summary(engine.into());
                    app.set_browser_summary(browser.into());
                    app.set_selected_download_index(-1);
                });
            }
        });
    }

    {
        let selected_queue = Arc::clone(&selected_queue);
        app.on_toggle_queue(move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let db_path = flow_db_path();
            if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
                let _ = repo.init_schema();
                if let Ok(Some(group)) = repo.get_queue_group(queue_id) {
                    if group.active {
                        if let Ok(jobs) = repo.list_queue_jobs() {
                            for job in jobs.into_iter().filter(|job| job.queue_id == queue_id) {
                                let _ = pause_active_job(&job.id);
                                let _ = repo.update_queue_job_status(&job.id, "Paused");
                            }
                        }
                    } else if let Ok(jobs) = repo.list_queue_jobs() {
                        for job in jobs.into_iter().filter(|job| job.queue_id == queue_id && job.status == "Paused") {
                            let _ = repo.update_queue_job_status(&job.id, "Queued");
                        }
                    }
                    let _ = repo.set_queue_group_active(queue_id, !group.active);
                }
            }
        });
    }

    {
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        app.on_delete_queue(move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            if queue_id == 0 {
                return;
            }
            let db_path = flow_db_path();
            if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
                let _ = repo.init_schema();
                let _ = repo.delete_queue_group(queue_id);
                if let Ok(mut selected) = selected_queue.lock() {
                    *selected = 0;
                }
                if let Ok(mut selected) = selected_download.lock() {
                    *selected = None;
                }
                let state = load_queue_ui_state(0);
                let weak2 = weak.clone();
                let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_selected_queue_index(0);
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
                    app.set_download_items(ModelRc::new(VecModel::from(state.row_labels)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    let (appearance, engine, browser) = load_settings_sections();
                    app.set_appearance_summary(appearance.into());
                    app.set_engine_summary(engine.into());
                    app.set_browser_summary(browser.into());
                    app.set_selected_download_index(-1);
                });
            }
        });
    }

    {
        let selected_queue_for_download = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        app.on_select_download(move |index| {
            let selected_queue_id = selected_queue_for_download.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(selected_queue_id);
            let chosen = state.row_ids.get(index as usize).cloned();
            if let Ok(mut selected) = selected_download.lock() {
                *selected = chosen;
            }
        });
    }

    wire_queue_item_controls(&app, Arc::clone(&selected_queue), Arc::clone(&selected_download));

    {
        let selected_queue = Arc::clone(&selected_queue);
        let weak = app.as_weak();
        app.on_select_queue(move |index| {
            let state = load_queue_ui_state(selected_queue.lock().map(|v| *v).unwrap_or(0));
            if let Some(queue_id) = state.queue_ids.get(index as usize).copied() {
                if let Ok(mut selected) = selected_queue.lock() {
                    *selected = queue_id;
                }
                let selected_now = selected_queue.lock().map(|v| *v).unwrap_or(0);
                let state = load_queue_ui_state(selected_now);
                let weak2 = weak.clone();
                let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_selected_queue_index(index);
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
                    app.set_download_items(ModelRc::new(VecModel::from(state.row_labels)));
                    app.set_queue_config_summary(state.queue_summary.into());
                });
            }
        });
    }

    let weak = app.as_weak();
    let selected_queue_for_thread = Arc::clone(&selected_queue);
    let selected_download_for_thread = Arc::clone(&selected_download);
    std::thread::spawn(move || {
        let (tx, rx) = channel();
        let mut watcher = RecommendedWatcher::new(tx, notify::Config::default()).expect("Failed to watch queue signal");
        let signal = flow_signal_path();
        let _ = std::fs::create_dir_all(signal.parent().unwrap_or_else(|| std::path::Path::new(".")));
        let _ = std::fs::write(&signal, b"0");
        watcher.watch(&signal, RecursiveMode::NonRecursive).expect("Failed to register queue signal watcher");

        while rx.recv().is_ok() {
            let selected = selected_queue_for_thread.lock().map(|v| *v).unwrap_or(0);
            let selected_download = selected_download_for_thread.lock().ok().and_then(|v| v.clone());
            let state = load_queue_ui_state(selected);
            let selected_index = selected_download
                .as_ref()
                .and_then(|id| state.row_ids.iter().position(|row_id| row_id == id))
                .map(|idx| idx as i32)
                .unwrap_or(-1);
            let weak2 = weak.clone();
            let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
                    app.set_download_items(ModelRc::new(VecModel::from(state.row_labels)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    let (appearance, engine, browser) = load_settings_sections();
                    app.set_appearance_summary(appearance.into());
                    app.set_engine_summary(engine.into());
                    app.set_browser_summary(browser.into());
                    app.set_clipboard_pending_summary(load_clipboard_pending_summary().into());
                    let settings = load_settings(&flow_data_dir().join("settings.json"));
                    app.set_proxy_host_text(settings.proxy.manual.host.into());
                    app.set_perhost_first_text(settings.per_host.first().map(|v| v.host.clone()).unwrap_or_default().into());
                    app.set_selected_download_index(selected_index);
                });
            }
        });

    app.run().expect("UI runtime error");
}

fn setup_tray_if_enabled(app: &MainWindow) -> Option<TrayContext> {
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    if !settings.use_system_tray {
        return None;
    }

    let menu = Menu::new();
    let show_hide = MenuItem::new("Show / Hide", true, None);
    let quit = MenuItem::new("Quit", true, None);
    let _ = menu.append(&show_hide);
    let _ = menu.append(&quit);

    let tray_icon = TrayIconBuilder::new()
        .with_menu(Box::new(menu))
        .with_tooltip("Flow Download Manager")
        .build()
        .ok()?;

    let show_hide_id = show_hide.id().clone();
    let quit_id = quit.id().clone();
    let app_weak = app.as_weak();
    let timer = slint::Timer::default();
    timer.start(
        slint::TimerMode::Repeated,
        std::time::Duration::from_millis(180),
        move || {
            while let Ok(event) = tray_icon::menu::MenuEvent::receiver().try_recv() {
                if event.id == show_hide_id {
                    let weak = app_weak.clone();
                    let _ = weak.upgrade_in_event_loop(|app| {
                        let visible = app.window().is_visible();
                        if visible {
                            app.window().hide().ok();
                        } else {
                            app.window().show().ok();
                        }
                    });
                } else if event.id == quit_id {
                    std::process::exit(0);
                }
            }
        },
    );

    Some(TrayContext {
        _tray_icon: tray_icon,
        _timer: timer,
    })
}

fn load_queue_ui_state(selected_queue_id: i64) -> QueueUiState {
    let db_path = flow_db_path();
    let Ok(repo) = SqliteDownloadRepository::open(&db_path) else {
        return QueueUiState { queue_labels: vec!["Main".into()], queue_ids: vec![0], row_labels: vec!["Queue database not available".into()], row_ids: vec![], queue_summary: "Queue database not available".to_string() };
    };
    if repo.init_schema().is_err() {
        return QueueUiState { queue_labels: vec!["Main".into()], queue_ids: vec![0], row_labels: vec!["Queue schema not available".into()], row_ids: vec![], queue_summary: "Queue schema not available".to_string() };
    }
    let groups_raw = repo
        .list_queue_groups()
        .unwrap_or_default();
    let groups = groups_raw.iter()
        .map(|group| format!("{}{}", if group.active { "" } else { "[Paused] " }, group.name).into())
        .collect::<Vec<SharedString>>();
    let group_ids = groups_raw.iter().map(|g| g.id).collect::<Vec<_>>();
    let summary = groups_raw.iter().find(|g| g.id == selected_queue_id)
        .map(|g| format!("{} | max {} | stop_on_empty {} | {}", g.name, g.max_concurrent, g.stop_on_empty, if g.active { "active" } else { "paused" }))
        .unwrap_or_else(|| "Queue config unavailable".to_string());
    let Ok(jobs) = repo.list_queue_view_rows() else {
        return QueueUiState { queue_labels: groups, queue_ids: group_ids, row_labels: vec!["Unable to load queue".into()], row_ids: vec![], queue_summary: summary };
    };
    let filtered_jobs = jobs
        .into_iter()
        .filter(|row| row.queue_id == selected_queue_id)
        .collect::<Vec<_>>();
    if filtered_jobs.is_empty() {
        return QueueUiState { queue_labels: groups, queue_ids: group_ids, row_labels: vec!["No downloads in this queue".into()], row_ids: vec![], queue_summary: summary };
    }

    let row_ids = filtered_jobs.iter().map(|row| row.id.clone()).collect::<Vec<_>>();
    let rows = filtered_jobs.into_iter()
        .take(100)
        .map(|row| {
            let progress = match row.total_bytes {
                Some(total) if total > 0 => format!("{:.1}%", (row.downloaded_bytes as f64 / total as f64) * 100.0),
                _ => "--".to_string(),
            };
            let error = row.last_error.map(|e| format!(" - {e}")).unwrap_or_default();
            format!(
                "[{}] {} - {} - {} - attempt {}{}",
                row.queue_name, row.file_name, row.status, progress, row.attempt_count, error
            )
            .into()
        })
        .collect();
    QueueUiState { queue_labels: groups, queue_ids: group_ids, row_labels: rows, row_ids, queue_summary: summary }
}

fn wire_queue_item_controls(app: &MainWindow, selected_queue: Arc<Mutex<i64>>, selected_download: Arc<Mutex<Option<String>>>) {
    app.on_move_download_up({
        let selected_download = Arc::clone(&selected_download);
        move || mutate_selected_job(selected_download.clone(), |repo, id| { let _ = repo.reorder_queue_job(id, -1); })
    });
    app.on_move_download_down({
        let selected_download = Arc::clone(&selected_download);
        move || mutate_selected_job(selected_download.clone(), |repo, id| { let _ = repo.reorder_queue_job(id, 1); })
    });
    app.on_requeue_download({
        let selected_download = Arc::clone(&selected_download);
        move || mutate_selected_job(selected_download.clone(), |repo, id| { let _ = repo.push_queue_job_to_end(id); })
    });
    app.on_queue_max_minus({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            group.max_concurrent = (group.max_concurrent - 1).max(1);
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_max_plus({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            group.max_concurrent += 1;
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_toggle_stop_on_empty({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            group.stop_on_empty = !group.stop_on_empty;
            let _ = repo.upsert_queue_group(&group);
        })
    });
}

fn load_settings_sections() -> (String, String, String) {
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    let appearance = format!(
        "auto_start={} (registry={}) | system_tray={} | folder={}",
        settings.auto_start,
        is_windows_auto_start_enabled("FlowUI").unwrap_or(false),
        settings.use_system_tray,
        settings.default_download_folder.clone().unwrap_or_else(default_downloads_folder),
    );
    let engine = format!(
        "thread_count={} | max_concurrent={} | per_host={} ({}) | proxy={:?}@{}:{} auth={}",
        settings.thread_count,
        settings.max_concurrent_downloads,
        settings.per_host.len(),
        settings.per_host.first().map(|v| v.host.as_str()).unwrap_or("no-rule"),
        settings.proxy.mode,
        settings.proxy.manual.host,
        settings.proxy.manual.port,
        if settings.proxy.manual.username.is_some() { "on" } else { "off" },
    );
    let browser = format!(
        "browser_integration={} | clipboard_monitoring={}",
        settings.browser_integration_enabled,
        settings.clipboard_monitoring
    );
    (appearance, engine, browser)
}

fn mutate_settings<F>(mutator: F)
where
    F: FnOnce(&mut flow_core::FlowSettings),
{
    let path = flow_data_dir().join("settings.json");
    let mut settings = load_settings(&path);
    mutator(&mut settings);
    let _ = save_settings(&path, &settings);
}

fn default_downloads_folder() -> String {
    std::env::var("USERPROFILE")
        .map(|home| std::path::PathBuf::from(home).join("Downloads").to_string_lossy().to_string())
        .unwrap_or_else(|_| "downloads".to_string())
}

fn load_clipboard_pending_json() -> Option<serde_json::Value> {
    let text = std::fs::read_to_string(flow_clipboard_pending_path()).ok()?;
    serde_json::from_str(&text).ok()
}

fn load_clipboard_pending_summary() -> String {
    let Some(value) = load_clipboard_pending_json() else {
        return String::new();
    };
    let url = value.get("url").and_then(|v| v.as_str()).unwrap_or("<invalid>");
    format!("Clipboard link pending: {url}")
}

fn mutate_selected_job<F>(selected_download: Arc<Mutex<Option<String>>>, op: F)
where
    F: Fn(&SqliteDownloadRepository, &str),
{
    let id = selected_download.lock().ok().and_then(|v| v.clone());
    let Some(id) = id else { return; };
    let db_path = flow_db_path();
    if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
        let _ = repo.init_schema();
        op(&repo, &id);
    }
}

fn mutate_selected_queue<F>(selected_queue: Arc<Mutex<i64>>, op: F)
where
    F: Fn(&SqliteDownloadRepository, flow_core::QueueGroupRecord),
{
    let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
    let db_path = flow_db_path();
    if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
        let _ = repo.init_schema();
        if let Ok(Some(group)) = repo.get_queue_group(queue_id) {
            op(&repo, group);
        }
    }
}
