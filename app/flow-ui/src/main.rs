#![windows_subsystem = "windows"]
slint::include_modules!();

use std::sync::mpsc::channel;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};
use std::collections::HashSet;

use flow_core::{flow_clipboard_decision_path, flow_clipboard_pending_path, flow_db_path, flow_signal_path, flow_data_dir, load_settings, pause_active_job, save_settings, set_windows_auto_start, DownloadRepository, PerHostSettings, QueueJobRecord, SqliteDownloadRepository};
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum SortColumn {
    Name,
    Size,
    Status,
    DateAdded,
}

#[derive(Debug, Clone, Copy)]
struct SortState {
    column: SortColumn,
    descending: bool,
}

impl Default for SortState {
    fn default() -> Self {
        Self {
            column: SortColumn::DateAdded,
            descending: true,
        }
    }
}

fn main() {
    flow_core::init();
    let app = MainWindow::new().expect("Failed to create main window");
    let selected_queue = Arc::new(Mutex::new(0_i64));
    let selected_download = Arc::new(Mutex::new(None::<String>));
    let sort_state = Arc::new(Mutex::new(SortState::default()));
    let queue_state = load_queue_ui_state(*selected_queue.lock().expect("selected queue lock"), &sort_state);
    app.set_queue_groups(ModelRc::new(VecModel::from(queue_state.queue_labels)));
    app.set_download_rows(ModelRc::new(VecModel::from(queue_state.rows)));
    app.set_queue_config_summary(queue_state.queue_summary.into());
    app.set_queue_name_text(load_selected_queue_name(*selected_queue.lock().expect("selected queue lock")).into());
    app.set_status_message("Ready".into());
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    app.set_default_folder_text(settings.default_download_folder.clone().unwrap_or_else(default_downloads_folder).into());
    app.set_browser_extension_id_text(settings.browser_extension_id.unwrap_or_default().into());
    app.set_thread_count(settings.thread_count as i32);
    app.set_max_concurrent(settings.max_concurrent_downloads as i32);
    app.set_auto_start_enabled(settings.auto_start);
    app.set_system_tray_enabled(settings.use_system_tray);
    app.set_browser_integration_enabled(settings.browser_integration_enabled);
    app.set_clipboard_monitoring_enabled(settings.clipboard_monitoring);
    app.set_proxy_mode(settings.proxy_mode as i32);

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
    app.on_settings_thread_minus(|| {
        mutate_settings(|settings| settings.thread_count = settings.thread_count.saturating_sub(1).max(1));
    });
    app.on_settings_thread_plus(|| {
        mutate_settings(|settings| settings.thread_count += 1);
    });
    app.on_settings_max_minus(|| {
        mutate_settings(|settings| settings.max_concurrent_downloads = settings.max_concurrent_downloads.saturating_sub(1).max(1));
    });
    app.on_settings_max_plus(|| {
        mutate_settings(|settings| settings.max_concurrent_downloads += 1);
    });
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
        let sort_state = Arc::clone(&sort_state);
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
                let state = load_queue_ui_state(next_id, &sort_state);
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
        let sort_state = Arc::clone(&sort_state);
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
                let state = load_queue_ui_state(0, &sort_state);
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
        let sort_state = Arc::clone(&sort_state);
        let weak = app.as_weak();
        app.on_select_download(move |index| {
            let selected_queue_id = selected_queue_for_download.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(selected_queue_id, &sort_state);
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
    wire_download_toolbar_actions(
        &app,
        Arc::clone(&selected_queue),
        Arc::clone(&selected_download),
        Arc::clone(&sort_state),
    );
    
    // Wire settings display handlers
    {
        app.on_proxy_mode_changed(move |mode| {
            // Store proxy mode in settings
            mutate_settings(|settings| {
                settings.proxy_mode = mode as u32;
            });
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_proxy_test_connection(move || {
            set_status(&weak, "Testing proxy connection...");
            // In real implementation, this would test the proxy
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_queue_create_new(move || {
            // Trigger create_queue callback
            weak.upgrade_in_event_loop(|app| app.invoke_create_queue()).ok();
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_queue_rename(move || {
            set_status(&weak, "Rename functionality coming soon");
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_queue_delete_confirm(move || {
            set_status(&weak, "Use Delete Queue button from Scheduler tab");
        });
    }
    
    {
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        app.on_retry_selected(move || {
            mutate_selected_job_with_status(selected_download.clone(), weak.clone(), "Queued for retry", |repo, id| {
                let _ = repo.update_queue_job_status(id, "Queued");
                let _ = repo.reset_queue_job_for_retry(id);
            })
        });
    }

    {
        let selected_queue = Arc::clone(&selected_queue);
        let sort_state = Arc::clone(&sort_state);
        let weak = app.as_weak();
        app.on_select_queue(move |index| {
            let state = load_queue_ui_state(selected_queue.lock().map(|v| *v).unwrap_or(0), &sort_state);
            if let Some(queue_id) = state.queue_ids.get(index as usize).copied() {
                if let Ok(mut selected) = selected_queue.lock() {
                    *selected = queue_id;
                }
                let selected_now = selected_queue.lock().map(|v| *v).unwrap_or(0);
                let state = load_queue_ui_state(selected_now, &sort_state);
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
    let sort_state_for_thread = Arc::clone(&sort_state);
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
            let state = load_queue_ui_state(selected, &sort_state_for_thread);
            let selected_index = selected_download
                .as_ref()
                .and_then(|id| state.row_ids.iter().position(|row_id| row_id == id))
                .map(|idx| idx as i32)
                .unwrap_or(-1);
            let weak2 = weak.clone();
            let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels.clone())));
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows.clone())));
                    app.set_queue_config_summary(state.queue_summary.clone().into());
                    app.set_queue_name_text(load_selected_queue_name(selected).into());
                    app.set_selected_download_index(selected_index);
                    
                    if let Some(pending) = load_clipboard_pending_json() {
                        let url = pending.get("url").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        let file_name = pending.get("file_name").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        let output_dir = pending.get("output_dir").and_then(|v| v.as_str()).unwrap_or_default().to_string();

                        if let Ok(dialog) = AddUrlDialog::new() {
                            dialog.set_url(url.clone().into());
                            dialog.set_file_name(file_name.into());
                            dialog.set_output_dir(output_dir.into());
                            dialog.set_category(infer_category_from_name_or_url("", &url).into());
                            dialog.set_queue_options(ModelRc::new(VecModel::from(state.queue_labels.clone())));
                            let selected_queue_index = state
                                .queue_ids
                                .iter()
                                .position(|id| *id == selected)
                                .unwrap_or(0) as i32;
                            dialog.set_queue_index(selected_queue_index);

                            let dialog_choose = dialog.as_weak();
                            dialog.on_choose_folder(move || {
                                if let Some(folder) = open_folder_picker() {
                                    if let Some(dlg) = dialog_choose.upgrade() {
                                        dlg.set_output_dir(folder.into());
                                    }
                                }
                            });

                            let dialog_weak1 = dialog.as_weak();
                            dialog.on_cancel(move || {
                                let mut decision = serde_json::Map::new();
                                decision.insert("action".to_string(), serde_json::Value::String("dismiss".to_string()));
                                let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                                let _ = std::fs::remove_file(flow_clipboard_pending_path());
                                if let Some(dlg) = dialog_weak1.upgrade() { dlg.hide().ok(); }
                            });

                            let queue_ids_for_later = state.queue_ids.clone();
                            let dialog_weak2 = dialog.as_weak();
                            dialog.on_download_later(move |_url, file_name, output_dir, queue_index, category| {
                                let pending_data = load_clipboard_pending_json().unwrap_or_default();
                                let mut decision = pending_data.as_object().cloned().unwrap_or_default();
                                let queue_id = queue_ids_for_later
                                    .get(queue_index as usize)
                                    .copied()
                                    .unwrap_or(selected);
                                decision.insert("action".to_string(), serde_json::Value::String("queue".to_string()));
                                decision.insert("file_name".to_string(), serde_json::Value::String(file_name.to_string()));
                                decision.insert("output_dir".to_string(), serde_json::Value::String(output_dir.to_string()));
                                decision.insert("queue_id".to_string(), serde_json::Value::Number(queue_id.into()));
                                decision.insert("category".to_string(), serde_json::Value::String(category.to_string()));
                                let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                                let _ = std::fs::remove_file(flow_clipboard_pending_path());
                                if let Some(dlg) = dialog_weak2.upgrade() { dlg.hide().ok(); }
                            });

                            let queue_ids_for_start = state.queue_ids.clone();
                            let dialog_weak3 = dialog.as_weak();
                            dialog.on_start_now(move |_url, file_name, output_dir, queue_index, category| {
                                let pending_data = load_clipboard_pending_json().unwrap_or_default();
                                let mut decision = pending_data.as_object().cloned().unwrap_or_default();
                                let queue_id = queue_ids_for_start
                                    .get(queue_index as usize)
                                    .copied()
                                    .unwrap_or(selected);
                                decision.insert("action".to_string(), serde_json::Value::String("queue_start".to_string()));
                                decision.insert("file_name".to_string(), serde_json::Value::String(file_name.to_string()));
                                decision.insert("output_dir".to_string(), serde_json::Value::String(output_dir.to_string()));
                                decision.insert("queue_id".to_string(), serde_json::Value::Number(queue_id.into()));
                                decision.insert("category".to_string(), serde_json::Value::String(category.to_string()));
                                let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                                let _ = std::fs::remove_file(flow_clipboard_pending_path());
                                if let Some(dlg) = dialog_weak3.upgrade() { dlg.hide().ok(); }
                            });

                            let _ = dialog.show();
                        }
                    }
                });
            }
        });

    // Menu callbacks
    app.on_menu_tasks({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_active_menu(0);
                app.set_status_message("Tasks menu opened".into());
            }
        }
    });

    app.on_menu_file({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_active_menu(1);
                app.set_status_message("File menu opened".into());
            }
        }
    });

    app.on_menu_downloads({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_active_menu(2);
                app.set_status_message("Downloads menu opened".into());
            }
        }
    });

    app.on_menu_view({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_active_menu(3);
                app.set_status_message("View menu opened".into());
            }
        }
    });

    app.on_menu_tools({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_active_menu(4);
                app.set_status_message("Tools menu opened".into());
            }
        }
    });

    app.on_menu_help({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_active_menu(5);
                app.set_status_message("Help menu opened".into());
            }
        }
    });

    app.on_tasks_start_all({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let mut resumed = 0usize;
            if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
                let _ = repo.init_schema();
                if let Ok(jobs) = repo.list_queue_jobs() {
                    for job in jobs.into_iter().filter(|job| job.queue_id == queue_id && job.status == "Paused") {
                        if repo.update_queue_job_status(&job.id, "Queued").is_ok() {
                            resumed += 1;
                        }
                    }
                }
                let _ = repo.set_queue_group_active(queue_id, true);
            }
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, &format!("Started {resumed} paused task(s)"));
        }
    });

    app.on_tasks_pause_all({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            pause_all_jobs(queue_id);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Paused all tasks in current queue");
        }
    });

    app.on_file_batch_download({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(queue_id, &sort_state);
            let queue_ids = state.queue_ids.clone();
            let selected_queue_index = queue_ids
                .iter()
                .position(|id| *id == queue_id)
                .unwrap_or(0) as i32;

            if let Ok(dialog) = BatchDownloadDialog::new() {
                dialog.set_output_dir(default_downloads_folder().into());
                dialog.set_category("General".into());
                dialog.set_queue_options(ModelRc::new(VecModel::from(state.queue_labels.clone())));
                dialog.set_queue_index(selected_queue_index);

                let dialog_choose = dialog.as_weak();
                dialog.on_choose_folder(move || {
                    if let Some(folder) = open_folder_picker() {
                        if let Some(dlg) = dialog_choose.upgrade() {
                            dlg.set_output_dir(folder.into());
                        }
                    }
                });

                let dialog_close = dialog.as_weak();
                dialog.on_close_dialog(move || {
                    if let Some(dlg) = dialog_close.upgrade() {
                        let _ = dlg.hide();
                    }
                });

                let weak_queue = weak.clone();
                let queue_ids_for_queue = queue_ids.clone();
                let selected_download_for_queue = Arc::clone(&selected_download);
                let sort_state_for_queue = Arc::clone(&sort_state);
                let dialog_queue = dialog.as_weak();
                dialog.on_queue_all(move |urls_text, output_dir, queue_index, category| {
                    let target_queue = queue_ids_for_queue.get(queue_index as usize).copied().unwrap_or(queue_id);
                    let (queued, invalid) = enqueue_batch_urls(
                        urls_text.as_str(),
                        output_dir.as_str(),
                        target_queue,
                        false,
                        category.as_str(),
                    );
                    refresh_queue_ui(&weak_queue, target_queue, selected_download_for_queue.clone(), &sort_state_for_queue);
                    set_status(&weak_queue, &format!("Batch queued: {queued}, invalid/skipped: {invalid}"));
                    if queued > 0 {
                        if let Some(dlg) = dialog_queue.upgrade() {
                            let _ = dlg.hide();
                        }
                    }
                });

                let weak_start = weak.clone();
                let queue_ids_for_start = queue_ids.clone();
                let selected_download_for_start = Arc::clone(&selected_download);
                let sort_state_for_start = Arc::clone(&sort_state);
                let dialog_start = dialog.as_weak();
                dialog.on_start_all(move |urls_text, output_dir, queue_index, category| {
                    let target_queue = queue_ids_for_start.get(queue_index as usize).copied().unwrap_or(queue_id);
                    let (queued, invalid) = enqueue_batch_urls(
                        urls_text.as_str(),
                        output_dir.as_str(),
                        target_queue,
                        true,
                        category.as_str(),
                    );
                    refresh_queue_ui(&weak_start, target_queue, selected_download_for_start.clone(), &sort_state_for_start);
                    set_status(&weak_start, &format!("Batch start queued: {queued}, invalid/skipped: {invalid}"));
                    if queued > 0 {
                        if let Some(dlg) = dialog_start.upgrade() {
                            let _ = dlg.hide();
                        }
                    }
                });

                let _ = dialog.show();
            }
        }
    });

    app.on_app_exit({
        let weak = app.as_weak();
        move || {
            if let Some(app) = weak.upgrade() {
                app.set_status_message("Exit requested".into());
            }
            std::process::exit(0);
        }
    });

    app.on_downloads_select_all({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(queue_id, &sort_state);
            let selected_id = state.row_ids.first().cloned();
            if let Ok(mut slot) = selected_download.lock() {
                *slot = selected_id;
            }
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Selected first item in current list");
        }
    });

    app.on_downloads_clear_selection({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            if let Ok(mut slot) = selected_download.lock() {
                *slot = None;
            }
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Selection cleared");
        }
    });

    app.on_downloads_sort_by_name({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            toggle_sort(&sort_state, SortColumn::Name);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Sorted by name");
        }
    });

    app.on_downloads_sort_by_size({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            toggle_sort(&sort_state, SortColumn::Size);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Sorted by size");
        }
    });

    app.on_downloads_sort_by_status({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            toggle_sort(&sort_state, SortColumn::Status);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Sorted by status");
        }
    });

    app.on_downloads_sort_by_date_added({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            toggle_sort(&sort_state, SortColumn::DateAdded);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Sorted by date added");
        }
    });

    app.on_view_refresh({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
            set_status(&weak, "Refreshed downloads view");
        }
    });

    app.on_view_toggle_toolbar({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                let visible = app.get_toolbar_visible();
                app.set_toolbar_visible(!visible);
                app.set_status_message((if visible { "Toolbar hidden" } else { "Toolbar shown" }).into());
            });
        }
    });

    app.on_view_toggle_status_bar({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                let visible = app.get_status_bar_visible();
                app.set_status_bar_visible(!visible);
                app.set_status_message((if visible { "Status bar hidden" } else { "Status bar shown" }).into());
            });
        }
    });

    app.on_tools_browser_integration({
        let weak = app.as_weak();
        move || {
            set_status(&weak, "Open Settings > Browser Integration");
        }
    });

    app.on_tools_per_host_settings({
        let weak = app.as_weak();
        move || {
            show_per_host_settings_dialog(weak.clone());
        }
    });

    app.on_tools_open_settings({
        let weak = app.as_weak();
        move || {
            set_status(&weak, "Settings tab opened");
        }
    });

    app.on_help_about({
        let weak = app.as_weak();
        move || {
            set_status(&weak, "Flow Download Manager - Rust + Slint build");
        }
    });

    app.on_help_check_updates({
        let weak = app.as_weak();
        move || {
            set_status(&weak, "Update check endpoint will be wired in next phase");
        }
    });

    app.on_help_donate({
        let weak = app.as_weak();
        move || {
            open_external("https://github.com/sponsors");
            set_status(&weak, "Opened donation page");
        }
    });

    app.on_help_translators({
        let weak = app.as_weak();
        move || {
            open_external("https://crowdin.com");
            set_status(&weak, "Opened translators page");
        }
    });

    app.on_help_online_help({
        let weak = app.as_weak();
        move || {
            open_external("https://github.com");
            set_status(&weak, "Opened online help");
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

fn load_queue_ui_state(selected_queue_id: i64, sort_state: &Arc<Mutex<SortState>>) -> QueueUiState {
    let db_path = flow_db_path();
    let Some(repo) = SqliteDownloadRepository::open(&db_path).ok() else {
        return QueueUiState { queue_labels: vec![], queue_ids: vec![], rows: vec![], row_ids: vec![], queue_summary: "DB Error".to_string() };
    };
    let _ = repo.init_schema();

    let queue_groups = repo.list_queue_groups().unwrap_or_default();
    let groups = queue_groups.iter().map(|g| g.name.clone().into()).collect();
    let group_ids = queue_groups.iter().map(|g| g.id).collect();
    let summary = repo.get_queue_group(selected_queue_id).ok().flatten()
        .map(|g| format!("{} | max {} | stop_on_empty {} | {}", g.name, g.max_concurrent, g.stop_on_empty, if g.active { "active" } else { "paused" }))
        .unwrap_or_else(|| "Queue config unavailable".to_string());
    let Ok(jobs) = repo.list_queue_view_rows() else {
        return QueueUiState { queue_labels: groups, queue_ids: group_ids, rows: vec![], row_ids: vec![], queue_summary: summary };
    };
    let mut filtered_jobs = jobs
        .into_iter()
        .filter(|row| row.queue_id == selected_queue_id)
        .collect::<Vec<_>>();

    let active_sort = sort_state.lock().map(|v| *v).unwrap_or_default();
    sort_queue_rows(&mut filtered_jobs, active_sort);

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
                date_added: format_date_added(row.created_at).into(),
                description: row.last_error.clone().unwrap_or_default().into(),
                progress: progress_percent,
                has_progress: row.total_bytes.unwrap_or(0) > 0 && row.status != "Queued",
                is_error: row.status == "Failed",
            }
        })
        .collect::<Vec<_>>();
    QueueUiState { queue_labels: groups, queue_ids: group_ids, rows, row_ids, queue_summary: summary }
}

fn sort_queue_rows(rows: &mut [flow_core::QueueViewRow], sort: SortState) {
    rows.sort_by(|a, b| {
        let base = match sort.column {
            SortColumn::Name => a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase()),
            SortColumn::Size => row_size(a).cmp(&row_size(b)),
            SortColumn::Status => a.status.to_lowercase().cmp(&b.status.to_lowercase()),
            SortColumn::DateAdded => a.created_at.cmp(&b.created_at),
        };
        if base == std::cmp::Ordering::Equal {
            a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase())
        } else {
            base
        }
    });
    if sort.descending {
        rows.reverse();
    }
}

fn row_size(row: &flow_core::QueueViewRow) -> u64 {
    row.total_bytes.unwrap_or(row.downloaded_bytes)
}

fn toggle_sort(sort_state: &Arc<Mutex<SortState>>, next_column: SortColumn) {
    if let Ok(mut sort) = sort_state.lock() {
        if sort.column == next_column {
            sort.descending = !sort.descending;
        } else {
            sort.column = next_column;
            sort.descending = match next_column {
                SortColumn::Name | SortColumn::Status => false,
                SortColumn::Size | SortColumn::DateAdded => true,
            };
        }
    }
}

fn format_date_added(created_at: i64) -> String {
    if created_at <= 0 {
        return "Unknown".to_string();
    }
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(created_at);
    let delta = (now - created_at).max(0);
    if delta < 60 {
        format!("{delta}s ago")
    } else if delta < 3600 {
        format!("{}m ago", delta / 60)
    } else if delta < 86_400 {
        format!("{}h ago", delta / 3600)
    } else {
        format!("{}d ago", delta / 86_400)
    }
}

fn refresh_queue_ui(
    weak: &slint::Weak<MainWindow>,
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
) {
    let state = load_queue_ui_state(queue_id, sort_state);
    let selected_queue_index = state
        .queue_ids
        .iter()
        .position(|id| *id == queue_id)
        .map(|idx| idx as i32)
        .unwrap_or(0);
    let selected_id = selected_download.lock().ok().and_then(|v| v.clone());
    let selected_index = selected_id
        .as_ref()
        .and_then(|id| state.row_ids.iter().position(|row_id| row_id == id))
        .map(|idx| idx as i32)
        .unwrap_or(-1);
    let _ = weak.upgrade_in_event_loop(move |app| {
        app.set_selected_queue_index(selected_queue_index);
        app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
        app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
        app.set_queue_config_summary(state.queue_summary.into());
        app.set_queue_name_text(load_selected_queue_name(queue_id).into());
        app.set_selected_download_index(selected_index);
    });
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

fn wire_download_toolbar_actions(
    app: &MainWindow,
    selected_queue: Arc<Mutex<i64>>,
    selected_download: Arc<Mutex<Option<String>>>,
    sort_state: Arc<Mutex<SortState>>,
) {
    app.on_add_url({
        let selected_queue = Arc::clone(&selected_queue);
        let sort_state = Arc::clone(&sort_state);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let queue_state = load_queue_ui_state(queue_id, &sort_state);
            let selected_queue_index = queue_state
                .queue_ids
                .iter()
                .position(|id| *id == queue_id)
                .unwrap_or(0) as i32;
            if let Ok(dialog) = AddUrlDialog::new() {
                dialog.set_url("".into());
                dialog.set_file_name("".into());
                dialog.set_output_dir(default_downloads_folder().into());
                dialog.set_category("General".into());
                dialog.set_queue_options(ModelRc::new(VecModel::from(queue_state.queue_labels.clone())));
                dialog.set_queue_index(selected_queue_index);
                let dialog_weak = dialog.as_weak();
                dialog.on_choose_folder(move || {
                    if let Some(folder) = open_folder_picker() {
                        if let Some(dlg) = dialog_weak.upgrade() {
                            dlg.set_output_dir(folder.into());
                        }
                    }
                });
                let dialog_weak = dialog.as_weak();
                dialog.on_cancel(move || {
                    if let Some(dlg) = dialog_weak.upgrade() { dlg.hide().ok(); }
                });
                let queue_ids_for_later = queue_state.queue_ids.clone();
                let dialog_weak = dialog.as_weak();
                let weak_later = weak.clone();
                dialog.on_download_later(move |url, file_name, output_dir, queue_index, category| {
                    let selected_queue_id = queue_ids_for_later
                        .get(queue_index as usize)
                        .copied()
                        .unwrap_or(queue_id);
                    let category = if category.trim().is_empty() {
                        infer_category_from_name_or_url(file_name.as_str(), url.as_str())
                    } else {
                        category.to_string()
                    };
                    enqueue_manual_url(selected_queue_id, url.as_str(), file_name.as_str(), output_dir.as_str(), false, category.as_str());
                    set_status(&weak_later, "Download added to queue as Paused");
                    if let Some(dlg) = dialog_weak.upgrade() { dlg.hide().ok(); }
                });
                let queue_ids_for_start = queue_state.queue_ids.clone();
                let dialog_weak = dialog.as_weak();
                let weak_now = weak.clone();
                dialog.on_start_now(move |url, file_name, output_dir, queue_index, category| {
                    let selected_queue_id = queue_ids_for_start
                        .get(queue_index as usize)
                        .copied()
                        .unwrap_or(queue_id);
                    let category = if category.trim().is_empty() {
                        infer_category_from_name_or_url(file_name.as_str(), url.as_str())
                    } else {
                        category.to_string()
                    };
                    enqueue_manual_url(selected_queue_id, url.as_str(), file_name.as_str(), output_dir.as_str(), true, category.as_str());
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
        let selected_download = Arc::clone(&selected_download);
        let sort_state = Arc::clone(&sort_state);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            pause_all_jobs(queue_id);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), &sort_state);
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

fn enqueue_manual_url(queue_id: i64, url: &str, file_name: &str, output_dir: &str, start_now: bool, _category: &str) {
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

fn infer_category_from_name_or_url(file_name: &str, url: &str) -> String {
    let candidate = if file_name.trim().is_empty() {
        infer_file_name(url)
    } else {
        file_name.trim().to_string()
    };
    let lower = candidate.to_lowercase();
    if lower.ends_with(".mp4") || lower.ends_with(".mkv") || lower.ends_with(".webm") || lower.ends_with(".avi") {
        return "Video".to_string();
    }
    if lower.ends_with(".mp3") || lower.ends_with(".wav") || lower.ends_with(".flac") || lower.ends_with(".m4a") {
        return "Music".to_string();
    }
    if lower.ends_with(".zip") || lower.ends_with(".rar") || lower.ends_with(".7z") || lower.ends_with(".tar") || lower.ends_with(".gz") {
        return "Compressed".to_string();
    }
    if lower.ends_with(".pdf") || lower.ends_with(".doc") || lower.ends_with(".docx") || lower.ends_with(".txt") {
        return "Documents".to_string();
    }
    "General".to_string()
}

fn is_http_url(value: &str) -> bool {
    let lower = value.trim().to_lowercase();
    lower.starts_with("http://") || lower.starts_with("https://")
}

fn parse_batch_urls(urls_text: &str) -> (Vec<String>, usize) {
    let mut unique = HashSet::new();
    let mut out = Vec::new();
    let mut invalid = 0usize;
    for token in urls_text
        .split(|ch: char| ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ' || ch == ',' || ch == ';')
        .map(str::trim)
        .filter(|token| !token.is_empty())
    {
        if !is_http_url(token) {
            invalid += 1;
            continue;
        }
        let normalized = token.to_string();
        if unique.insert(normalized.clone()) {
            out.push(normalized);
        }
    }
    (out, invalid)
}

fn enqueue_batch_urls(urls_text: &str, output_dir: &str, queue_id: i64, start_now: bool, category: &str) -> (usize, usize) {
    let (urls, invalid) = parse_batch_urls(urls_text);
    for url in &urls {
        enqueue_manual_url(queue_id, url, "", output_dir, start_now, category);
    }
    (urls.len(), invalid)
}

fn show_per_host_settings_dialog(weak: slint::Weak<MainWindow>) {
    let settings_path = flow_data_dir().join("settings.json");
    let initial = load_settings(&settings_path);
    let entries = Arc::new(Mutex::new(initial.per_host));
    let current_index = Arc::new(Mutex::new(0usize));

    let Ok(dialog) = PerHostSettingsDialog::new() else {
        set_status(&weak, "Unable to open Per-host Settings dialog");
        return;
    };

    sync_per_host_dialog(&dialog, entries.clone(), current_index.clone());

    {
        let dialog_weak = dialog.as_weak();
        let entries = entries.clone();
        let current_index = current_index.clone();
        dialog.on_prev_entry(move || {
            if let Ok(mut idx) = current_index.lock() {
                if *idx > 0 {
                    *idx -= 1;
                }
            }
            if let Some(dlg) = dialog_weak.upgrade() {
                sync_per_host_dialog(&dlg, entries.clone(), current_index.clone());
            }
        });
    }

    {
        let dialog_weak = dialog.as_weak();
        let entries = entries.clone();
        let current_index = current_index.clone();
        dialog.on_next_entry(move || {
            if let (Ok(mut idx), Ok(items)) = (current_index.lock(), entries.lock()) {
                if !items.is_empty() && *idx + 1 < items.len() {
                    *idx += 1;
                }
            }
            if let Some(dlg) = dialog_weak.upgrade() {
                sync_per_host_dialog(&dlg, entries.clone(), current_index.clone());
            }
        });
    }

    {
        let dialog_weak = dialog.as_weak();
        let entries = entries.clone();
        let current_index = current_index.clone();
        dialog.on_add_entry(move || {
            if let Ok(mut items) = entries.lock() {
                items.push(PerHostSettings {
                    host: "".to_string(),
                    username: None,
                    password: None,
                    user_agent: None,
                    thread_count: None,
                });
                if let Ok(mut idx) = current_index.lock() {
                    *idx = items.len().saturating_sub(1);
                }
            }
            if let Some(dlg) = dialog_weak.upgrade() {
                sync_per_host_dialog(&dlg, entries.clone(), current_index.clone());
            }
        });
    }

    {
        let dialog_weak = dialog.as_weak();
        let weak_status = weak.clone();
        let entries = entries.clone();
        let current_index = current_index.clone();
        dialog.on_save_entry(move |host, username, password, user_agent, thread_count_text| {
            let host = host.trim().to_string();
            if host.is_empty() {
                set_status(&weak_status, "Host pattern cannot be empty");
                return;
            }
            let thread_count = if thread_count_text.trim().is_empty() {
                None
            } else {
                match thread_count_text.trim().parse::<usize>() {
                    Ok(v) if (1..=64).contains(&v) => Some(v),
                    _ => {
                        set_status(&weak_status, "Thread count must be 1-64 or empty");
                        return;
                    }
                }
            };

            if let (Ok(mut items), Ok(idx)) = (entries.lock(), current_index.lock()) {
                if items.is_empty() {
                    items.push(PerHostSettings {
                        host: host.clone(),
                        username: none_if_empty(username.as_str()),
                        password: none_if_empty(password.as_str()),
                        user_agent: none_if_empty(user_agent.as_str()),
                        thread_count,
                    });
                } else {
                    let index = (*idx).min(items.len().saturating_sub(1));
                    items[index] = PerHostSettings {
                        host: host.clone(),
                        username: none_if_empty(username.as_str()),
                        password: none_if_empty(password.as_str()),
                        user_agent: none_if_empty(user_agent.as_str()),
                        thread_count,
                    };
                }
                save_per_host_entries(items.as_slice());
            }
            set_status(&weak_status, "Per-host entry saved");
            if let Some(dlg) = dialog_weak.upgrade() {
                sync_per_host_dialog(&dlg, entries.clone(), current_index.clone());
            }
        });
    }

    {
        let dialog_weak = dialog.as_weak();
        let weak_status = weak.clone();
        let entries = entries.clone();
        let current_index = current_index.clone();
        dialog.on_delete_entry(move || {
            if let (Ok(mut items), Ok(mut idx)) = (entries.lock(), current_index.lock()) {
                if !items.is_empty() {
                    let remove_at = (*idx).min(items.len().saturating_sub(1));
                    items.remove(remove_at);
                    if *idx > 0 {
                        *idx -= 1;
                    }
                    save_per_host_entries(items.as_slice());
                    set_status(&weak_status, "Per-host entry deleted");
                } else {
                    set_status(&weak_status, "No per-host entry to delete");
                }
            }
            if let Some(dlg) = dialog_weak.upgrade() {
                sync_per_host_dialog(&dlg, entries.clone(), current_index.clone());
            }
        });
    }

    {
        let dialog_weak = dialog.as_weak();
        dialog.on_close_dialog(move || {
            if let Some(dlg) = dialog_weak.upgrade() {
                let _ = dlg.hide();
            }
        });
    }

    let _ = dialog.show();
}

fn sync_per_host_dialog(dialog: &PerHostSettingsDialog, entries: Arc<Mutex<Vec<PerHostSettings>>>, index: Arc<Mutex<usize>>) {
    let items = entries.lock().map(|v| v.clone()).unwrap_or_default();
    let mut idx = index.lock().map(|v| *v).unwrap_or(0);
    if !items.is_empty() && idx >= items.len() {
        idx = items.len() - 1;
    }
    let total = items.len();
    let (host, username, password, user_agent, thread_count_text) = if total == 0 {
        (
            String::new(),
            String::new(),
            String::new(),
            String::new(),
            String::new(),
        )
    } else {
        let item = &items[idx];
        (
            item.host.clone(),
            item.username.clone().unwrap_or_default(),
            item.password.clone().unwrap_or_default(),
            item.user_agent.clone().unwrap_or_default(),
            item.thread_count.map(|v| v.to_string()).unwrap_or_default(),
        )
    };
    if let Ok(mut write_idx) = index.lock() {
        *write_idx = idx;
    }
    dialog.set_entry_position(format!("{}/{}", if total == 0 { 0 } else { idx + 1 }, total).into());
    dialog.set_host_pattern(host.into());
    dialog.set_username(username.into());
    dialog.set_password(password.into());
    dialog.set_user_agent(user_agent.into());
    dialog.set_thread_count_text(thread_count_text.into());
}

fn save_per_host_entries(entries: &[PerHostSettings]) {
    mutate_settings(|settings| {
        settings.per_host = entries.to_vec();
    });
}

fn none_if_empty(value: &str) -> Option<String> {
    let value = value.trim();
    if value.is_empty() {
        None
    } else {
        Some(value.to_string())
    }
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

fn open_external(url: &str) {
    let _ = std::process::Command::new("explorer.exe").arg(url).spawn();
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_batch_urls_keeps_unique_valid_urls() {
        let input = "https://a.com/file1\nhttps://a.com/file1, http://b.com/file2 ; not-a-url";
        let (urls, invalid) = parse_batch_urls(input);
        assert_eq!(urls.len(), 2);
        assert!(urls.contains(&"https://a.com/file1".to_string()));
        assert!(urls.contains(&"http://b.com/file2".to_string()));
        assert_eq!(invalid, 1);
    }

    #[test]
    fn parse_batch_urls_trims_and_splits_multiple_delimiters() {
        let input = "  https://x.com/a   ;https://x.com/b\n\nhttp://x.com/c  ";
        let (urls, invalid) = parse_batch_urls(input);
        assert_eq!(invalid, 0);
        assert_eq!(urls, vec![
            "https://x.com/a".to_string(),
            "https://x.com/b".to_string(),
            "http://x.com/c".to_string(),
        ]);
    }

    #[test]
    fn infer_category_from_name_or_url_maps_common_extensions() {
        assert_eq!(infer_category_from_name_or_url("movie.mkv", ""), "Video");
        assert_eq!(infer_category_from_name_or_url("song.mp3", ""), "Music");
        assert_eq!(infer_category_from_name_or_url("archive.zip", ""), "Compressed");
        assert_eq!(infer_category_from_name_or_url("guide.pdf", ""), "Documents");
        assert_eq!(infer_category_from_name_or_url("", "https://site.com/file.bin"), "General");
    }

    #[test]
    fn none_if_empty_returns_none_for_blank_strings() {
        assert_eq!(none_if_empty("   "), None);
        assert_eq!(none_if_empty("abc"), Some("abc".to_string()));
    }
}
