#![windows_subsystem = "windows"]
slint::include_modules!();

use std::sync::mpsc::channel;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use flow_core::{flow_clipboard_decision_path, flow_clipboard_pending_path, flow_db_path, flow_signal_path, flow_data_dir, load_settings, pause_active_job, save_settings, set_windows_auto_start, DownloadRepository, QueueJobRecord, SqliteDownloadRepository};
use notify::{RecommendedWatcher, RecursiveMode, Watcher};
use slint::{CloseRequestResponse, ModelRc, SharedString, VecModel};
use tray_icon::menu::{Menu, MenuItem};
use tray_icon::TrayIconBuilder;

struct TrayContext {
    _tray_icon: tray_icon::TrayIcon,
    _timer: slint::Timer,
}

struct QueueUiState {
    queue_labels: Vec<SharedString>,
    queue_ids: Vec<i64>,
    rows: Vec<DownloadRow>,
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
    app.set_download_rows(ModelRc::new(VecModel::from(queue_state.rows)));
    app.set_queue_config_summary(queue_state.queue_summary.into());
    app.set_queue_name_text(load_selected_queue_name(*selected_queue.lock().expect("selected queue lock")).into());
    app.set_status_message("Ready".into());
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    app.set_default_folder_text(settings.default_download_folder.clone().unwrap_or_else(default_downloads_folder).into());
    app.set_browser_extension_id_text(settings.browser_extension_id.unwrap_or_default().into());

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
    app.on_settings_use_downloads_folder(|| {
        if let Some(folder) = open_folder_picker() {
            mutate_settings(|settings| settings.default_download_folder = Some(folder));
        }
    });
    
    {
        let selected_queue = Arc::clone(&selected_queue);
        app.on_queue_name_changed(move |value| {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let db_path = flow_db_path();
            if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
                let _ = repo.init_schema();
                if let Ok(Some(mut group)) = repo.get_queue_group(queue_id) {
                    if !value.trim().is_empty() {
                        group.name = value.to_string();
                        let _ = repo.upsert_queue_group(&group);
                    }
                }
            }
        });
    }



    app.on_register_browser_host(|| {
        let settings = load_settings(&flow_data_dir().join("settings.json"));
        let Some(extension_id) = settings.browser_extension_id else {
            return;
        };
        let Some(script) = locate_register_script() else {
            return;
        };
        let host_exe = std::env::current_exe()
            .ok()
            .and_then(|path| path.parent().map(|dir| dir.join("flow-host.exe")))
            .unwrap_or_else(|| std::path::PathBuf::from("flow-host.exe"))
            .to_string_lossy()
            .to_string();
        let _ = std::process::Command::new("powershell")
            .args([
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script.as_str(),
                "-ExtensionId",
                extension_id.as_str(),
                "-FirefoxExtensionId",
                "flow_download_manager@example.com",
                "-HostExe",
                host_exe.as_str(),
            ])
            .status();
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
                    schedule_json: None,
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
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    app.set_queue_name_text(load_selected_queue_name(next_id).into());
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
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    app.set_queue_name_text(load_selected_queue_name(0).into());
                    app.set_selected_download_index(-1);
                });
            }
        });
    }

    {
        let selected_queue_for_download = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        app.on_select_download(move |index| {
            let selected_queue_id = selected_queue_for_download.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(selected_queue_id);
            let chosen = state.row_ids.get(index as usize).cloned();
            if let Ok(mut selected) = selected_download.lock() {
                *selected = chosen;
            }
            let _ = weak.upgrade_in_event_loop(move |app| {
                app.set_selected_download_index(index);
            });
        });
    }

    wire_queue_item_controls(&app, Arc::clone(&selected_queue), Arc::clone(&selected_download));
    wire_download_toolbar_actions(&app, Arc::clone(&selected_queue), Arc::clone(&selected_download));

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
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    app.set_queue_name_text(load_selected_queue_name(selected_now).into());
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
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    app.set_queue_name_text(load_selected_queue_name(selected).into());
                    app.set_selected_download_index(selected_index);
                    
                    if let Some(pending) = load_clipboard_pending_json() {
                        let url = pending.get("url").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        let file_name = pending.get("file_name").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        let output_dir = pending.get("output_dir").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        
                        let dialog = ClipboardDialog::new().unwrap();
                        dialog.set_url(url.into());
                        dialog.set_file_name(file_name.into());
                        dialog.set_output_dir(output_dir.into());
                        
                        let dialog_weak1 = dialog.as_weak();
                        dialog.on_dismiss(move || {
                            let mut decision = serde_json::Map::new();
                            decision.insert("action".to_string(), serde_json::Value::String("dismiss".to_string()));
                            let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                            let _ = std::fs::remove_file(flow_clipboard_pending_path());
                            if let Some(dlg) = dialog_weak1.upgrade() { dlg.hide().ok(); }
                        });
                        
                        let dialog_weak2 = dialog.as_weak();
                        dialog.on_queue_later(move || {
                            let pending_data = load_clipboard_pending_json().unwrap_or_default();
                            let mut decision = pending_data.as_object().cloned().unwrap_or_default();
                            decision.insert("action".to_string(), serde_json::Value::String("queue".to_string()));
                            let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                            let _ = std::fs::remove_file(flow_clipboard_pending_path());
                            if let Some(dlg) = dialog_weak2.upgrade() { dlg.hide().ok(); }
                        });

                        let dialog_weak3 = dialog.as_weak();
                        dialog.on_start_now(move || {
                            let pending_data = load_clipboard_pending_json().unwrap_or_default();
                            let mut decision = pending_data.as_object().cloned().unwrap_or_default();
                            decision.insert("action".to_string(), serde_json::Value::String("queue_start".to_string()));
                            let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                            let _ = std::fs::remove_file(flow_clipboard_pending_path());
                            if let Some(dlg) = dialog_weak3.upgrade() { dlg.hide().ok(); }
                        });
                        
                        dialog.show().unwrap();
                    }
                });
            }
        });

    app.run().expect("UI runtime error");
}

fn load_selected_queue_name(queue_id: i64) -> String {
    let db_path = flow_db_path();
    let Ok(repo) = SqliteDownloadRepository::open(&db_path) else {
        return "Main".to_string();
    };
    let _ = repo.init_schema();
    repo.get_queue_group(queue_id)
        .ok()
        .flatten()
        .map(|group| group.name)
        .unwrap_or_else(|| "Main".to_string())
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

    app.window().on_close_requested(|| CloseRequestResponse::HideWindow);

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
                    slint::quit_event_loop().ok();
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
    let Some(repo) = SqliteDownloadRepository::open(&db_path).ok() else {
        return QueueUiState { queue_labels: vec![], queue_ids: vec![], rows: vec![], row_ids: vec![], queue_summary: "DB Error".to_string() };
    };
    let _ = repo.init_schema();

    let groups = repo.list_queue_groups().unwrap_or_default().into_iter().map(|g| g.name.into()).collect();
    let group_ids = repo.list_queue_groups().unwrap_or_default().into_iter().map(|g| g.id).collect();
    let summary = repo.get_queue_group(selected_queue_id).ok().flatten()
        .map(|g| format!("{} | max {} | stop_on_empty {} | {}", g.name, g.max_concurrent, g.stop_on_empty, if g.active { "active" } else { "paused" }))
        .unwrap_or_else(|| "Queue config unavailable".to_string());
    let Ok(jobs) = repo.list_queue_view_rows() else {
        return QueueUiState { queue_labels: groups, queue_ids: group_ids, rows: vec![], row_ids: vec![], queue_summary: summary };
    };
    let filtered_jobs = jobs
        .into_iter()
        .filter(|row| row.queue_id == selected_queue_id)
        .collect::<Vec<_>>();
    if filtered_jobs.is_empty() {
        return QueueUiState { queue_labels: groups, queue_ids: group_ids, rows: vec![], row_ids: vec![], queue_summary: summary };
    }

    let row_ids = filtered_jobs.iter().map(|row| row.id.clone()).collect::<Vec<_>>();
    let rows = filtered_jobs.iter()
        .take(100)
        .map(|row| {
            let progress_percent = match row.total_bytes {
                Some(total) if total > 0 => ((row.downloaded_bytes as f64 / total as f64) * 100.0).round().clamp(0.0, 100.0) as i32,
                _ => 0,
            };
            let size = match row.total_bytes {
                Some(total) if total > 0 => format_bytes(total),
                _ => "Unknown".to_string(),
            };
            DownloadRow {
                checked: false,
                name: row.file_name.clone().into(),
                category: row.queue_name.clone().into(),
                size: size.into(),
                status: format_status(&row.status, progress_percent).into(),
                speed: if row.status == "Downloading" { "calculating".into() } else { "--".into() },
                time_left: "--".into(),
                date_added: format!("attempt {}", row.attempt_count).into(),
                description: row.last_error.clone().unwrap_or_default().into(),
                progress: progress_percent,
                has_progress: row.total_bytes.unwrap_or(0) > 0 && row.status != "Queued",
                is_error: row.status == "Failed",
            }
        })
        .collect::<Vec<_>>();
    QueueUiState { queue_labels: groups, queue_ids: group_ids, rows, row_ids, queue_summary: summary }
}

fn format_bytes(bytes: u64) -> String {
    const UNITS: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    let mut value = bytes as f64;
    let mut unit = 0;
    while value >= 1024.0 && unit < UNITS.len() - 1 {
        value /= 1024.0;
        unit += 1;
    }
    if unit == 0 { format!("{} {}", bytes, UNITS[unit]) } else { format!("{value:.1} {}", UNITS[unit]) }
}

fn format_status(status: &str, progress: i32) -> String {
    match status {
        "Queued" => "Added".to_string(),
        "Paused" => if progress > 0 { format!("{progress}% Paused") } else { "Paused".to_string() },
        "Downloading" => format!("{progress}% Downloading"),
        "Completed" => "Finished".to_string(),
        "Failed" => "Error".to_string(),
        "Cancelled" => "Canceled".to_string(),
        other => other.to_string(),
    }
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

fn wire_download_toolbar_actions(app: &MainWindow, selected_queue: Arc<Mutex<i64>>, selected_download: Arc<Mutex<Option<String>>>) {
    app.on_add_url({
        let selected_queue = Arc::clone(&selected_queue);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            if let Ok(dialog) = AddUrlDialog::new() {
                dialog.set_output_dir(default_downloads_folder().into());
                let dialog_weak = dialog.as_weak();
                dialog.on_cancel(move || {
                    if let Some(dlg) = dialog_weak.upgrade() { dlg.hide().ok(); }
                });
                let dialog_weak = dialog.as_weak();
                let weak_later = weak.clone();
                dialog.on_download_later(move |url, file_name, output_dir| {
                    enqueue_manual_url(queue_id, url.as_str(), file_name.as_str(), output_dir.as_str(), false);
                    set_status(&weak_later, "Download added to queue as Paused");
                    if let Some(dlg) = dialog_weak.upgrade() { dlg.hide().ok(); }
                });
                let dialog_weak = dialog.as_weak();
                let weak_now = weak.clone();
                dialog.on_start_now(move |url, file_name, output_dir| {
                    enqueue_manual_url(queue_id, url.as_str(), file_name.as_str(), output_dir.as_str(), true);
                    set_status(&weak_now, "Download queued to start");
                    if let Some(dlg) = dialog_weak.upgrade() { dlg.hide().ok(); }
                });
                let _ = dialog.show();
            } else {
                set_status(&weak, "Unable to open Add URL dialog");
            }
        }
    });
    app.on_resume_selected({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Download resumed", |repo, id| { let _ = repo.update_queue_job_status(id, "Queued"); })
    });
    app.on_stop_selected({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Download paused", |repo, id| {
            let _ = pause_active_job(id);
            let _ = repo.update_queue_job_status(id, "Paused");
        })
    });
    app.on_stop_all({
        let selected_queue = Arc::clone(&selected_queue);
        let weak = app.as_weak();
        move || {
            pause_all_jobs(selected_queue.lock().map(|v| *v).unwrap_or(0));
            set_status(&weak, "Stopped all downloads in selected queue");
        }
    });
    app.on_request_delete_selected({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            if selected_download.lock().ok().and_then(|v| v.clone()).is_none() {
                set_status(&weak, "No download selected");
                return;
            }
            let _ = weak.upgrade_in_event_loop(|app| app.set_show_delete_confirm(true));
        }
    });
    app.on_cancel_delete_selected({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                app.set_show_delete_confirm(false);
                app.set_status_message("Delete cancelled".into());
            });
        }
    });
    app.on_confirm_delete_selected({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Deleted selected download", |repo, id| {
                let _ = pause_active_job(id);
                let _ = repo.delete_download_job(id);
            });
            let _ = weak.upgrade_in_event_loop(|app| app.set_show_delete_confirm(false));
        }
    });
    app.on_delete_selected({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Deleted selected download", |repo, id| {
            let _ = pause_active_job(id);
            let _ = repo.delete_download_job(id);
        })
    });
    app.on_open_selected({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            open_selected_path(selected_download.clone(), false);
            set_status(&weak, "Opening selected file");
        }
    });
    app.on_open_selected_folder({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            open_selected_path(selected_download.clone(), true);
            set_status(&weak, "Opening selected folder");
        }
    });
    app.on_move_download_up({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Moved up", |repo, id| { let _ = repo.reorder_queue_job(id, -1); })
    });
    app.on_move_download_down({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Moved down", |repo, id| { let _ = repo.reorder_queue_job(id, 1); })
    });
    app.on_requeue_download({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Requeued", |repo, id| { let _ = repo.update_queue_job_status(id, "Queued"); })
    });
}

fn open_folder_picker() -> Option<String> {
    let script = r#"
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
    $dialog.Description = "Select Download Folder"
    if ($dialog.ShowDialog() -eq "OK") {
        $dialog.SelectedPath
    }
    "#;
    let output = std::process::Command::new("powershell")
        .args(["-Command", script])
        .output()
        .ok()?;
    if output.status.success() {
        let path = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if !path.is_empty() {
            return Some(path);
        }
    }
    None
}

fn locate_register_script() -> Option<String> {
    let exe_dir = std::env::current_exe().ok()?.parent()?.to_path_buf();
    let installed = exe_dir.join("native-messaging").join("windows").join("register-host.ps1");
    if installed.exists() {
        return Some(installed.to_string_lossy().to_string());
    }
    // Check dev path
    let dev = std::path::PathBuf::from(r"D:\Repos\Flow\app\native-messaging\windows\register-host.ps1");
    if dev.exists() {
        return Some(dev.to_string_lossy().to_string());
    }
    None
}

fn enqueue_manual_url(queue_id: i64, url: &str, file_name: &str, output_dir: &str, start_now: bool) {
    let url = url.trim();
    if url.is_empty() { return; }
    let output_dir = if output_dir.trim().is_empty() { default_downloads_folder() } else { output_dir.trim().to_string() };
    let file_name = if file_name.trim().is_empty() { infer_file_name(url) } else { file_name.trim().to_string() };
    let millis = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0);
    let job = QueueJobRecord {
        id: format!("manual-{millis}"),
        queue_id,
        url: url.to_string(),
        output_dir,
        file_name,
        connections: load_settings(&flow_data_dir().join("settings.json")).thread_count,
        expected_sha256_hex: None,
        headers_json: None,
        referrer: None,
        cookies: None,
        user_agent: None,
        username: None,
        password: None,
        proxy_url: None,
        proxy_username: None,
        proxy_password: None,
        status: if start_now { "Queued" } else { "Paused" }.to_string(),
        priority: 0,
        queue_order: millis as i64,
        attempt_count: 0,
        last_error: None,
    };
    if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
        let _ = repo.init_schema();
        let _ = repo.upsert_queue_job(&job);
    }
}

fn infer_file_name(url: &str) -> String {
    url.split('?').next().unwrap_or(url)
        .rsplit('/')
        .next()
        .filter(|name| !name.trim().is_empty())
        .unwrap_or("download.bin")
        .to_string()
}

fn pause_all_jobs(queue_id: i64) {
    if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
        let _ = repo.init_schema();
        if let Ok(jobs) = repo.list_queue_jobs() {
            for job in jobs.into_iter().filter(|job| queue_id == 0 || job.queue_id == queue_id) {
                let _ = pause_active_job(&job.id);
                let _ = repo.update_queue_job_status(&job.id, "Paused");
            }
        }
    }
}

fn open_selected_path(selected_download: Arc<Mutex<Option<String>>>, folder: bool) {
    let Some(id) = selected_download.lock().ok().and_then(|v| v.clone()) else { return; };
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return; };
    let _ = repo.init_schema();
    let Ok(Some(job)) = repo.get_queue_job(&id) else { return; };
    let file_path = std::path::PathBuf::from(&job.output_dir).join(&job.file_name);
    let target = if folder { std::path::PathBuf::from(&job.output_dir) } else { file_path };
    let _ = std::process::Command::new("explorer.exe").arg(target).spawn();
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

fn set_status(weak: &slint::Weak<MainWindow>, message: &str) {
    let message = SharedString::from(message);
    let _ = weak.upgrade_in_event_loop(move |app| app.set_status_message(message));
}

fn mutate_selected_job_with_status<F>(selected_download: Arc<Mutex<Option<String>>>, weak: slint::Weak<MainWindow>, success: &str, op: F)
where
    F: Fn(&SqliteDownloadRepository, &str),
{
    let id = selected_download.lock().ok().and_then(|v| v.clone());
    let Some(id) = id else {
        set_status(&weak, "No download selected");
        return;
    };
    match SqliteDownloadRepository::open(&flow_db_path()) {
        Ok(repo) => {
            let _ = repo.init_schema();
            op(&repo, &id);
            set_status(&weak, success);
        }
        Err(_) => set_status(&weak, "Queue database is not available"),
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
