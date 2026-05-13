#![windows_subsystem = "windows"]
slint::include_modules!();

mod add_url_actions;
mod home_action_descriptors;
mod home_action_menu_presentation;
mod home_action_menu_state;
mod home_action_registry;
mod home_action_state;
mod home_action_status;
mod home_actions;
mod queue_actions;
mod selection_model;

use std::sync::mpsc::channel;
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use std::collections::HashSet;

use add_url_actions::{prepare_manual_download_submission, render_add_url_error, render_add_url_preview, resolve_manual_category, resolve_manual_file_name, resolve_manual_output_dir};
use home_action_descriptors::{derive_downloads_menu_presentation, derive_home_action_descriptors, DownloadsMenuPresentation, HomeActionId};
use home_action_menu_presentation::{apply_downloads_menu_presentation, map_submenu_rows};
use home_action_registry::{HomeActionRegistry, derive_home_action_registry, execute_copy_as_curl, execute_copy_selected_links, execute_delete_selected, execute_move_to_category, execute_move_to_queue, execute_open_edit_dialog, execute_open_file_checksum_dialog, execute_open_file_or_properties, execute_pause_selected, execute_restart_selected, execute_resume_selected, execute_show_selected_properties};
use home_action_state::{derive_home_action_state, HomeActionState};
use home_action_status::{classify_download_activity, DownloadActivity};


use queue_actions::{clear_selection_after_delete, effective_checked_ids, mutate_selected_job_with_status, open_multiple_selected_paths, pause_all_jobs, selected_open_result, stop_all_result};
use selection_model::{clear_selection, select_all_visible, set_main_selection, sync_selection_to_visible_rows, toggle_item_selection};
use flow_core::{flow_clipboard_decision_path, flow_clipboard_pending_path, flow_db_path, flow_signal_path, flow_data_dir, load_settings, pause_active_job, save_settings, set_windows_auto_start, DownloadRepository, PerHostSettings, QueueJobRecord, SqliteDownloadRepository};
use home_action_registry::FileChecksumDialogPayload;
use notify::{RecommendedWatcher, RecursiveMode, Watcher};
use sha2::{Digest, Sha256};
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
    active_count: i32,
    total_jobs: i32,
    downloaded_bytes: u64,
    total_bytes: u64,
    active_speed_bytes_per_sec: u64,
}

#[derive(Debug, Clone)]
struct ProgressSample {
    downloaded_bytes: u64,
    observed_at: SystemTime,
}

static DOWNLOAD_PROGRESS_CACHE: std::sync::LazyLock<Mutex<std::collections::HashMap<String, ProgressSample>>> =
    std::sync::LazyLock::new(|| Mutex::new(std::collections::HashMap::new()));

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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum CategoryFilter {
    All,
    Unfinished,
    Finished,
}

impl CategoryFilter {
    fn from_index(index: i32) -> Self {
        match index {
            1 => Self::Unfinished,
            2 => Self::Finished,
            _ => Self::All,
        }
    }
}

fn main() {
    flow_core::init();
    let app = MainWindow::new().expect("Failed to create main window");
    let selected_queue = Arc::new(Mutex::new(0_i64));
    let selected_download = Arc::new(Mutex::new(None::<String>));
    let checked_downloads = Arc::new(Mutex::new(std::collections::HashSet::<String>::new()));
    let sort_state = Arc::new(Mutex::new(SortState::default()));
    let category_filter = Arc::new(Mutex::new(CategoryFilter::All));
    let queue_state = load_queue_ui_state(
        *selected_queue.lock().expect("selected queue lock"),
        &sort_state,
        &category_filter,
    );
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
    let initial_queue_id = *selected_queue.lock().expect("selected queue lock");
    let scheduler_state = load_queue_scheduler_state(initial_queue_id);
    app.set_queue_stop_on_empty(scheduler_state.stop_on_empty);
    app.set_queue_schedule_enabled(scheduler_state.enabled);
    app.set_queue_schedule_start(scheduler_state.start_text.into());
    app.set_queue_schedule_stop(scheduler_state.stop_text.into());
    app.set_queue_day_sun(scheduler_state.days[0]);
    app.set_queue_day_mon(scheduler_state.days[1]);
    app.set_queue_day_tue(scheduler_state.days[2]);
    app.set_queue_day_wed(scheduler_state.days[3]);
    app.set_queue_day_thu(scheduler_state.days[4]);
    app.set_queue_day_fri(scheduler_state.days[5]);
    app.set_queue_day_sat(scheduler_state.days[6]);

    let __tray_context = setup_tray_if_enabled(&app);

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
        let checked_downloads_for_create = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
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
                if let Ok(mut checked) = checked_downloads_for_create.lock() {
                    checked.clear();
                }
                let state = load_queue_ui_state(next_id, &sort_state, &category_filter);
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
        let checked_downloads_for_delete_queue = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
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
                if let Ok(mut checked) = checked_downloads_for_delete_queue.lock() {
                    checked.clear();
                }
                let state = load_queue_ui_state(0, &sort_state, &category_filter);
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
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        app.on_select_download(move |index| {
            let selected_queue_id = selected_queue_for_download.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(selected_queue_id, &sort_state, &category_filter);
            let chosen = set_main_selection(&state.row_ids, index, selected_download.clone());
            if chosen.is_none() {
                set_status(&weak, "Unable to select this row");
                return;
            }
            let _ = sync_selection_to_visible_rows(&state.row_ids, selected_download.clone(), checked_downloads.clone());
            let _ = weak.upgrade_in_event_loop(move |app| {
                app.set_selected_download_index(index);
            });
        });
    }

    {
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        app.on_toggle_download_checked(move |index| {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(queue_id, &sort_state, &category_filter);
            let Some((checked_count, became_checked, _row_id)) = toggle_item_selection(&state.row_ids, index, selected_download.clone(), checked_downloads.clone()) else {
                set_status(&weak, "Unable to toggle selection for this row");
                return;
            };
            refresh_queue_ui(
                &weak,
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            let action = if became_checked { "Selected" } else { "Unselected" };
            if checked_count == 0 {
                set_status(&weak, "No downloads selected");
            } else {
                set_status(&weak, &format!("{action} item • {checked_count} selected"));
            }
        });
    }

    wire_queue_item_controls(&app, Arc::clone(&selected_queue), Arc::clone(&selected_download));
    wire_download_toolbar_actions(
        &app,
        Arc::clone(&selected_queue),
        Arc::clone(&selected_download),
        Arc::clone(&checked_downloads),
        Arc::clone(&sort_state),
        Arc::clone(&category_filter),
    );
    
    {
        app.on_proxy_mode_changed(move |mode| {
            mutate_settings(|settings| {
                settings.proxy_mode = mode as u32;
            });
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_proxy_test_connection(move || {
            let settings = load_settings(&flow_data_dir().join("settings.json"));
            let message = validate_proxy_settings(&settings);
            set_status(&weak, &message);
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_queue_create_new(move || {
            weak.upgrade_in_event_loop(|app| app.invoke_create_queue()).ok();
        });
    }
    
    {
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads_for_queue_rename = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        app.on_queue_rename(move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            if queue_id == 0 {
                set_status(&weak, "Main queue cannot be renamed from this dialog");
                return;
            }
            let queue_name = load_selected_queue_name(queue_id);
            match RenameQueueDialog::new() {
                Ok(dialog) => {
                    dialog.set_queue_name(queue_name.clone().into());
                    let dialog_cancel = dialog.as_weak();
                    dialog.on_cancel(move || {
                        if let Some(dlg) = dialog_cancel.upgrade() {
                            let _ = dlg.hide();
                        }
                    });
                    let dialog_save = dialog.as_weak();
                    let weak_save = weak.clone();
                    let selected_download_for_save = Arc::clone(&selected_download);
                    let checked_downloads_for_save = Arc::clone(&checked_downloads_for_queue_rename);
                    let sort_state_for_save = Arc::clone(&sort_state);
                    let category_filter_for_save = Arc::clone(&category_filter);
                    dialog.on_save(move |value| {
                        match rename_queue_with_desktop_style(queue_id, value.as_str()) {
                            Ok(message) => {
                                refresh_queue_ui(
                                    &weak_save,
                                    queue_id,
                                    selected_download_for_save.clone(),
                                    checked_downloads_for_save.clone(),
                                    &sort_state_for_save,
                                    &category_filter_for_save,
                                );
                                set_status(&weak_save, &message);
                                if let Some(dlg) = dialog_save.upgrade() {
                                    let _ = dlg.hide();
                                }
                            }
                            Err(message) => set_status(&weak_save, &message),
                        }
                    });
                    let _ = dialog.show();
                }
                Err(_) => set_status(&weak, "Unable to open Rename Queue dialog"),
            }
        });
    }
    
    {
        let weak = app.as_weak();
        app.on_queue_delete_confirm(move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                app.set_current_tab(1);
                app.invoke_delete_queue();
            });
        });
    }
    
    {
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        app.on_retry_selected(move || {
            let result = mutate_selected_job_with_status(selected_download.clone(), "Queued for retry", "No download selected to retry", |repo, id| {
                let before = repo.get_queue_job(id).ok().flatten();
                let _ = repo.update_queue_job_status(id, "Queued");
                let _ = repo.reset_queue_job_for_retry(id);
                before.map(|job| job.status != "Queued").unwrap_or(true)
            });
            set_status(&weak, &result.status_message);
        });
    }

    {
        let selected_queue = Arc::clone(&selected_queue);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        app.on_select_queue(move |index| {
            let state = load_queue_ui_state(selected_queue.lock().map(|v| *v).unwrap_or(0), &sort_state, &category_filter);
            if let Some(queue_id) = state.queue_ids.get(index as usize).copied() {
                if let Ok(mut selected) = selected_queue.lock() {
                    *selected = queue_id;
                }
                let selected_now = selected_queue.lock().map(|v| *v).unwrap_or(0);
                let state = load_queue_ui_state(selected_now, &sort_state, &category_filter);
                let scheduler_state = load_queue_scheduler_state(selected_now);
                let weak2 = weak.clone();
                let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_selected_queue_index(index);
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
                    app.set_queue_config_summary(state.queue_summary.into());
                    app.set_queue_name_text(load_selected_queue_name(selected_now).into());
                    app.set_queue_stop_on_empty(scheduler_state.stop_on_empty);
                    app.set_queue_schedule_enabled(scheduler_state.enabled);
                    app.set_queue_schedule_start(scheduler_state.start_text.into());
                    app.set_queue_schedule_stop(scheduler_state.stop_text.into());
                    app.set_queue_day_sun(scheduler_state.days[0]);
                    app.set_queue_day_mon(scheduler_state.days[1]);
                    app.set_queue_day_tue(scheduler_state.days[2]);
                    app.set_queue_day_wed(scheduler_state.days[3]);
                    app.set_queue_day_thu(scheduler_state.days[4]);
                    app.set_queue_day_fri(scheduler_state.days[5]);
                    app.set_queue_day_sat(scheduler_state.days[6]);
                });
            }
        });
    }

    {
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads_for_category = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        app.on_select_category(move |index| {
            if let Ok(mut filter) = category_filter.lock() {
                *filter = CategoryFilter::from_index(index);
            }
            if let Ok(mut selected) = selected_download.lock() {
                *selected = None;
            }
            if let Ok(mut checked) = checked_downloads_for_category.lock() {
                checked.clear();
            }
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(
                &weak,
                queue_id,
                selected_download.clone(),
                checked_downloads_for_category.clone(),
                &sort_state,
                &category_filter,
            );
            let label = match CategoryFilter::from_index(index) {
                CategoryFilter::All => "All Downloads",
                CategoryFilter::Unfinished => "Unfinished",
                CategoryFilter::Finished => "Finished",
            };
            set_status(&weak, &format!("Category filter: {label}"));
        });
    }

    let weak = app.as_weak();
    let selected_queue_for_thread = Arc::clone(&selected_queue);
    let selected_download_for_thread = Arc::clone(&selected_download);
    let checked_downloads_for_thread = Arc::clone(&checked_downloads);
    let sort_state_for_thread = Arc::clone(&sort_state);
    let category_filter_for_thread = Arc::clone(&category_filter);
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
            let checked_downloads = checked_downloads_for_thread.lock().map(|v| v.clone()).unwrap_or_default();
            let state = load_queue_ui_state(selected, &sort_state_for_thread, &category_filter_for_thread);
            let state = apply_checked_rows(state, &checked_downloads);
            let selected_index = selected_download
                .as_ref()
                .and_then(|id| state.row_ids.iter().position(|row_id| row_id == id))
                .map(|idx| idx as i32)
                .unwrap_or(-1);
            let action_state = derive_home_action_state(&state.row_ids, &state.rows, &checked_downloads, selected_download.as_ref());
            let registry = derive_home_action_registry(
                selected,
                action_state,
                &sort_state_for_thread,
                &category_filter_for_thread,
            );
            let descriptors = derive_home_action_descriptors(&registry);
            let downloads_menu = derive_downloads_menu_presentation(&descriptors);
            let weak2 = weak.clone();
            let weak_for_clipboard = weak.clone();
            let _ = weak2.upgrade_in_event_loop(move |app| {
                    app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels.clone())));
                    app.set_download_rows(ModelRc::new(VecModel::from(state.rows.clone())));
                    app.set_queue_config_summary(state.queue_summary.clone().into());
                    app.set_queue_name_text(load_selected_queue_name(selected).into());
                    app.set_selected_download_index(selected_index);
                    apply_downloads_menu_presentation(&app, &downloads_menu);
                    
                    if let Some(pending) = load_clipboard_pending_json() {
                        let url = pending.get("url").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        let file_name = pending.get("file_name").and_then(|v| v.as_str()).unwrap_or_default().to_string();
                        let output_dir = pending.get("output_dir").and_then(|v| v.as_str()).unwrap_or_default().to_string();

                        if let Ok(dialog) = ClipboardDialog::new() {
                            let resolved_output_dir = if output_dir.trim().is_empty() { resolve_default_download_folder() } else { output_dir.clone() };
                            let resolved_file_name = resolve_manual_file_name(url.as_str(), file_name.as_str());
                            let resolved_category = resolve_manual_category(resolved_file_name.as_str(), url.as_str(), "General");
                            dialog.set_url(url.clone().into());
                            dialog.set_file_name(file_name.into());
                            dialog.set_output_dir(resolved_output_dir.clone().into());
                            dialog.set_resolved_file_name(resolved_file_name.into());
                            dialog.set_resolved_output_dir(resolved_output_dir.clone().into());
                            dialog.set_category_hint(resolved_category.clone().into());
                            dialog.set_dialog_hint("Review and confirm this clipboard download request.".into());
                            dialog.set_url_valid(is_http_url(url.as_str()));
                            dialog.set_category(resolved_category.into());
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
                                        dlg.set_output_dir(folder.clone().into());
                                        dlg.set_resolved_output_dir(folder.into());
                                    }
                                }
                            });

                            let dialog_weak1 = dialog.as_weak();
                            let weak_dismiss = weak_for_clipboard.clone();
                            dialog.on_dismiss(move || {
                                let mut decision = serde_json::Map::new();
                                decision.insert("action".to_string(), serde_json::Value::String("dismiss".to_string()));
                                let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                                let _ = std::fs::remove_file(flow_clipboard_pending_path());
                                set_status(&weak_dismiss, "Clipboard download dismissed");
                                if let Some(dlg) = dialog_weak1.upgrade() { dlg.hide().ok(); }
                            });

                            let queue_ids_for_later = state.queue_ids.clone();
                            let clipboard_url_for_later = url.clone();
                            let dialog_weak2 = dialog.as_weak();
                            let weak_queue = weak_for_clipboard.clone();
                            dialog.on_queue_later(move |file_name, output_dir, queue_index, category| {
                                let submit = match prepare_manual_download_submission(clipboard_url_for_later.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str()) {
                                    Ok(submit) => submit,
                                    Err(message) => {
                                        set_status(&weak_queue, &message);
                                        if let Some(dlg) = dialog_weak2.upgrade() {
                                            dlg.set_url_valid(false);
                                            dlg.set_dialog_hint(message.clone().into());
                                            dlg.set_resolved_file_name(resolve_manual_file_name(clipboard_url_for_later.as_str(), file_name.as_str()).into());
                                            dlg.set_resolved_output_dir(resolve_manual_output_dir(output_dir.as_str()).into());
                                            dlg.set_category_hint(resolve_manual_category(file_name.as_str(), clipboard_url_for_later.as_str(), category.as_str()).into());
                                        }
                                        return;
                                    }
                                };
                                let pending_data = load_clipboard_pending_json().unwrap_or_default();
                                let mut decision = pending_data.as_object().cloned().unwrap_or_default();
                                let queue_id = queue_ids_for_later
                                    .get(queue_index as usize)
                                    .copied()
                                    .unwrap_or(selected);
                                decision.insert("action".to_string(), serde_json::Value::String("queue".to_string()));
                                decision.insert("file_name".to_string(), serde_json::Value::String(submit.file_name));
                                decision.insert("output_dir".to_string(), serde_json::Value::String(submit.output_dir));
                                decision.insert("queue_id".to_string(), serde_json::Value::Number(queue_id.into()));
                                decision.insert("category".to_string(), serde_json::Value::String(submit.category.clone()));
                                let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                                let _ = std::fs::remove_file(flow_clipboard_pending_path());
                                set_status(&weak_queue, &format!("Clipboard download queued in {}", submit.category));
                                if let Some(dlg) = dialog_weak2.upgrade() { dlg.hide().ok(); }
                            });

                            let queue_ids_for_start = state.queue_ids.clone();
                            let clipboard_url_for_start = url.clone();
                            let dialog_weak3 = dialog.as_weak();
                            let weak_start = weak_for_clipboard.clone();
                            dialog.on_start_now(move |file_name, output_dir, queue_index, category| {
                                let submit = match prepare_manual_download_submission(clipboard_url_for_start.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str()) {
                                    Ok(submit) => submit,
                                    Err(message) => {
                                        set_status(&weak_start, &message);
                                        if let Some(dlg) = dialog_weak3.upgrade() {
                                            dlg.set_url_valid(false);
                                            dlg.set_dialog_hint(message.clone().into());
                                            dlg.set_resolved_file_name(resolve_manual_file_name(clipboard_url_for_start.as_str(), file_name.as_str()).into());
                                            dlg.set_resolved_output_dir(resolve_manual_output_dir(output_dir.as_str()).into());
                                            dlg.set_category_hint(resolve_manual_category(file_name.as_str(), clipboard_url_for_start.as_str(), category.as_str()).into());
                                        }
                                        return;
                                    }
                                };
                                let pending_data = load_clipboard_pending_json().unwrap_or_default();
                                let mut decision = pending_data.as_object().cloned().unwrap_or_default();
                                let queue_id = queue_ids_for_start
                                    .get(queue_index as usize)
                                    .copied()
                                    .unwrap_or(selected);
                                decision.insert("action".to_string(), serde_json::Value::String("queue_start".to_string()));
                                decision.insert("file_name".to_string(), serde_json::Value::String(submit.file_name));
                                decision.insert("output_dir".to_string(), serde_json::Value::String(submit.output_dir));
                                decision.insert("queue_id".to_string(), serde_json::Value::Number(queue_id.into()));
                                decision.insert("category".to_string(), serde_json::Value::String(submit.category.clone()));
                                let _ = std::fs::write(flow_clipboard_decision_path(), serde_json::Value::Object(decision).to_string());
                                let _ = std::fs::remove_file(flow_clipboard_pending_path());
                                set_status(&weak_start, &format!("Clipboard download queued to start in {}", submit.category));
                                if let Some(dlg) = dialog_weak3.upgrade() { dlg.hide().ok(); }
                            });

                            let _ = dialog.show();
                        }
                    }
                });
            }
        });

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
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
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
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            if resumed == 0 {
                set_status(&weak, "No paused tasks found in current queue");
            } else {
                set_status(&weak, &format!("Started {resumed} paused task(s)"));
            }
        }
    });

    app.on_tasks_pause_all({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let mut paused = 0usize;
            if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
                let _ = repo.init_schema();
                if let Ok(jobs) = repo.list_queue_jobs() {
                    for job in jobs.into_iter().filter(|job| job.queue_id == queue_id && job.status != "Paused") {
                        let _ = pause_active_job(&job.id);
                        if repo.update_queue_job_status(&job.id, "Paused").is_ok() {
                            paused += 1;
                        }
                    }
                }
                let _ = repo.set_queue_group_active(queue_id, false);
            }
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            if paused == 0 {
                set_status(&weak, "No active tasks found in current queue");
            } else {
                set_status(&weak, &format!("Paused {paused} task(s) in current queue"));
            }
        }
    });

    app.on_file_batch_download({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(queue_id, &sort_state, &category_filter);
            let queue_ids = state.queue_ids.clone();
            let selected_queue_index = queue_ids
                .iter()
                .position(|id| *id == queue_id)
                .unwrap_or(0) as i32;

            if let Ok(dialog) = BatchDownloadDialog::new() {
                let default_output_dir = resolve_default_download_folder();
                dialog.set_output_dir(default_output_dir.clone().into());
                dialog.set_resolved_output_dir(default_output_dir.clone().into());
                dialog.set_parsed_url_count(0);
                dialog.set_category("General".into());
                dialog.set_category_hint("General".into());
                dialog.set_dialog_hint("Paste one or more HTTP/HTTPS links to queue or start.".into());
                dialog.set_urls_valid(true);
                dialog.set_queue_options(ModelRc::new(VecModel::from(state.queue_labels.clone())));
                dialog.set_queue_index(selected_queue_index);

                let refresh_batch_preview = |dlg: &BatchDownloadDialog, urls_text: &str, output_dir: &str, category: &str| {
                    let parsed_links = urls_text
                        .split(|c: char| c.is_whitespace() || c == ',')
                        .filter(|token| is_http_url(token.trim()))
                        .count();
                    let resolved_output_dir = resolve_manual_output_dir(output_dir);
                    let normalized_category = normalize_category_input("", urls_text, category);
                    dlg.set_parsed_url_count(parsed_links as i32);
                    dlg.set_resolved_output_dir(resolved_output_dir.into());
                    dlg.set_category_hint(normalized_category.clone().into());
                    let has_non_empty_input = !urls_text.trim().is_empty();
                    if !has_non_empty_input {
                        dlg.set_urls_valid(true);
                        dlg.set_dialog_hint("Paste one or more HTTP/HTTPS links to queue or start.".into());
                    } else if parsed_links == 0 {
                        dlg.set_urls_valid(false);
                        dlg.set_dialog_hint("No valid HTTP/HTTPS links detected yet.".into());
                    } else {
                        dlg.set_urls_valid(true);
                        dlg.set_dialog_hint(format!("Ready to process {parsed_links} detected link(s)." ).into());
                    }
                };

                let dialog_preview = dialog.as_weak();
                dialog.on_preview_update(move |urls_text, output_dir, category| {
                    if let Some(dlg) = dialog_preview.upgrade() {
                        refresh_batch_preview(&dlg, urls_text.as_str(), output_dir.as_str(), category.as_str());
                    }
                });

                let dialog_choose = dialog.as_weak();
                dialog.on_choose_folder(move || {
                    if let Some(folder) = open_folder_picker() {
                        if let Some(dlg) = dialog_choose.upgrade() {
                            dlg.set_output_dir(folder.clone().into());
                            refresh_batch_preview(&dlg, dlg.get_urls_text().as_str(), folder.as_str(), dlg.get_category().as_str());
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
                let selected_queue_for_queue = Arc::clone(&selected_queue);
                let selected_download_for_queue = Arc::clone(&selected_download);
                let checked_downloads_for_queue = Arc::clone(&checked_downloads);
                let sort_state_for_queue = Arc::clone(&sort_state);
                let category_filter_for_queue = Arc::clone(&category_filter);
                let dialog_queue = dialog.as_weak();
                dialog.on_queue_all(move |urls_text, output_dir, queue_index, category| {
                    let normalized_category = normalize_category_input("", urls_text.as_str(), category.as_str());
                    if let Some(dlg) = dialog_queue.upgrade() {
                        refresh_batch_preview(&dlg, urls_text.as_str(), output_dir.as_str(), category.as_str());
                    }
                    let target_queue = queue_ids_for_queue.get(queue_index as usize).copied().unwrap_or(queue_id);
                    let (queued, invalid) = enqueue_batch_urls(
                        urls_text.as_str(),
                        output_dir.as_str(),
                        target_queue,
                        false,
                        normalized_category.as_str(),
                    );
                    if queued == 0 {
                        let message = if invalid > 0 {
                            format!("No valid URLs were queued. Invalid/skipped: {invalid}")
                        } else {
                            "No valid URLs to queue".to_string()
                        };
                        set_status(&weak_queue, &message);
                        if let Some(dlg) = dialog_queue.upgrade() {
                            dlg.set_urls_valid(false);
                            dlg.set_dialog_hint(message.into());
                        }
                        return;
                    }
                    if let Ok(mut selected) = selected_queue_for_queue.lock() {
                        *selected = target_queue;
                    }
                    refresh_queue_ui(&weak_queue, target_queue, selected_download_for_queue.clone(), checked_downloads_for_queue.clone(), &sort_state_for_queue, &category_filter_for_queue);
                    set_status(&weak_queue, &format!("Batch queued: {queued} item(s) in {normalized_category}; invalid/skipped: {invalid}"));
                    if let Some(dlg) = dialog_queue.upgrade() {
                        dlg.set_urls_valid(true);
                        let _ = dlg.hide();
                    }
                });

                let weak_start = weak.clone();
                let queue_ids_for_start = queue_ids.clone();
                let selected_queue_for_start = Arc::clone(&selected_queue);
                let selected_download_for_start = Arc::clone(&selected_download);
                let checked_downloads_for_start = Arc::clone(&checked_downloads);
                let sort_state_for_start = Arc::clone(&sort_state);
                let category_filter_for_start = Arc::clone(&category_filter);
                let dialog_start = dialog.as_weak();
                dialog.on_start_all(move |urls_text, output_dir, queue_index, category| {
                    let normalized_category = normalize_category_input("", urls_text.as_str(), category.as_str());
                    if let Some(dlg) = dialog_start.upgrade() {
                        refresh_batch_preview(&dlg, urls_text.as_str(), output_dir.as_str(), category.as_str());
                    }
                    let target_queue = queue_ids_for_start.get(queue_index as usize).copied().unwrap_or(queue_id);
                    let (queued, invalid) = enqueue_batch_urls(
                        urls_text.as_str(),
                        output_dir.as_str(),
                        target_queue,
                        true,
                        normalized_category.as_str(),
                    );
                    if queued == 0 {
                        let message = if invalid > 0 {
                            format!("No valid URLs were started. Invalid/skipped: {invalid}")
                        } else {
                            "No valid URLs to start".to_string()
                        };
                        set_status(&weak_start, &message);
                        if let Some(dlg) = dialog_start.upgrade() {
                            dlg.set_urls_valid(false);
                            dlg.set_dialog_hint(message.into());
                        }
                        return;
                    }
                    if let Ok(mut selected) = selected_queue_for_start.lock() {
                        *selected = target_queue;
                    }
                    refresh_queue_ui(&weak_start, target_queue, selected_download_for_start.clone(), checked_downloads_for_start.clone(), &sort_state_for_start, &category_filter_for_start);
                    set_status(&weak_start, &format!("Batch start queued: {queued} item(s) in {normalized_category}; invalid/skipped: {invalid}"));
                    if let Some(dlg) = dialog_start.upgrade() {
                        dlg.set_urls_valid(true);
                        let _ = dlg.hide();
                    }
                });

                let _ = dialog.show();
            }
        }
    });

    app.on_file_open_add_url({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                app.invoke_add_url();
            });
        }
    });

    app.on_file_open_batch_download({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                app.invoke_file_batch_download();
            });
        }
    });

    app.on_file_open_settings({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| {
                app.set_current_tab(2);
                app.set_status_message("Settings tab opened".into());
            });
        }
    });

    app.on_tasks_delete_finished({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let removed = delete_jobs_for_current_queue(queue_id, &["Completed"], false);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            if removed == 0 {
                set_status(&weak, "No finished items found in current queue");
            } else {
                set_status(&weak, &format!("Deleted {removed} finished item(s) from current queue"));
            }
        }
    });

    app.on_tasks_delete_unfinished({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let removed = delete_jobs_for_current_queue(queue_id, &["Queued", "Downloading", "Paused", "Failed", "Cancelled"], false);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            if removed == 0 {
                set_status(&weak, "No unfinished items found in current queue");
            } else {
                set_status(&weak, &format!("Deleted {removed} unfinished item(s) from current queue"));
            }
        }
    });

    app.on_tasks_delete_current_queue({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let removed = delete_jobs_for_current_queue(queue_id, &[], true);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            if removed == 0 {
                set_status(&weak, "Current queue is already empty");
            } else {
                set_status(&weak, &format!("Deleted {removed} item(s) from current queue"));
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
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let state = load_queue_ui_state(queue_id, &sort_state, &category_filter);
            let count = select_all_visible(&state.row_ids, selected_download.clone(), checked_downloads.clone());
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &format!("Selected {count} item(s)"));
        }
    });

    app.on_downloads_clear_selection({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            clear_selection(selected_download.clone(), checked_downloads.clone());
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, "Selection cleared");
        }
    });

    app.on_move_selection_to_queue({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move |target_index| {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = derive_home_action_registry(
                queue_id,
                current_home_action_state(queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter),
                &sort_state,
                &category_filter,
            );
            let Ok(result) = execute_move_to_queue(&registry, target_index) else {
                set_status(&weak, "Unable to resolve move-to-queue target");
                return;
            };
            clear_selection(selected_download.clone(), checked_downloads.clone());
            if let Ok(mut selected) = selected_queue.lock() {
                *selected = result.target_queue_id;
            }
            refresh_queue_ui(&weak, result.target_queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &format!("Moved {} download(s) to {}", result.moved_count, result.target_queue_name));
        }
    });

    app.on_move_selection_to_category({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move |target_index| {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = derive_home_action_registry(
                queue_id,
                current_home_action_state(queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter),
                &sort_state,
                &category_filter,
            );
            let Ok(result) = execute_move_to_category(&registry, target_index) else {
                set_status(&weak, "Unable to resolve move-to-category target");
                return;
            };
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &format!("Moved {} download(s) to category {}", result.moved_count, result.category));
        }
    });

    app.on_downloads_sort_by_name({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            toggle_sort(&sort_state, SortColumn::Name);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, "Sorted by name");
        }
    });

    app.on_downloads_sort_by_size({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            toggle_sort(&sort_state, SortColumn::Size);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, "Sorted by size");
        }
    });

    app.on_downloads_sort_by_status({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            toggle_sort(&sort_state, SortColumn::Status);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, "Sorted by status");
        }
    });

    app.on_downloads_sort_by_date_added({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            toggle_sort(&sort_state, SortColumn::DateAdded);
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, "Sorted by date added");
        }
    });

    app.on_view_refresh({
        let weak = app.as_weak();
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
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
            let _ = weak.upgrade_in_event_loop(|app| {
                app.set_current_tab(2);
                app.set_status_message("Opened Settings > Browser Integration".into());
            });
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
            let _ = weak.upgrade_in_event_loop(|app| {
                app.set_current_tab(2);
                app.set_status_message("Settings tab opened".into());
            });
        }
    });

    app.on_help_about({
        let weak = app.as_weak();
        move || {
            set_status(&weak, &build_about_message());
        }
    });

    app.on_help_check_updates({
        let weak = app.as_weak();
        move || {
            open_external("https://github.com/luuconghoangnam/flow");
            set_status(&weak, "Opened Flow project page for updates");
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
            open_external("https://github.com/luuconghoangnam/flow/blob/main/README.md");
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

fn queue_scheduler_state_default() -> QueueSchedulerState {
    QueueSchedulerState {
        stop_on_empty: false,
        enabled: false,
        start_text: "08:00".to_string(),
        stop_text: "18:00".to_string(),
        days: [true, true, true, true, true, true, true],
    }
}

#[derive(Clone)]
struct QueueSchedulerState {
    stop_on_empty: bool,
    enabled: bool,
    start_text: String,
    stop_text: String,
    days: [bool; 7],
}

fn load_queue_scheduler_state(queue_id: i64) -> QueueSchedulerState {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return queue_scheduler_state_default();
    };
    let _ = repo.init_schema();
    let Ok(Some(group)) = repo.get_queue_group(queue_id) else {
        return queue_scheduler_state_default();
    };
    let schedule = parse_schedule_config(group.schedule_json.as_deref());
    QueueSchedulerState {
        stop_on_empty: group.stop_on_empty,
        enabled: schedule.enabled,
        start_text: format_schedule_time(schedule.start_time_minutes),
        stop_text: format_schedule_time(schedule.stop_time_minutes),
        days: schedule_days_to_flags(&schedule.days_of_week),
    }
}

#[derive(Clone)]
struct UiScheduleConfig {
    enabled: bool,
    start_time_minutes: u32,
    stop_time_minutes: u32,
    days_of_week: Vec<u8>,
}

fn parse_schedule_config(config_json: Option<&str>) -> UiScheduleConfig {
    let default = UiScheduleConfig {
        enabled: false,
        start_time_minutes: 8 * 60,
        stop_time_minutes: 18 * 60,
        days_of_week: vec![0, 1, 2, 3, 4, 5, 6],
    };
    let Some(json) = config_json else {
        return default;
    };
    let Ok(value) = serde_json::from_str::<serde_json::Value>(json) else {
        return default;
    };
    let enabled = value.get("enabled").and_then(|v| v.as_bool()).unwrap_or(default.enabled);
    let start_time_minutes = value
        .get("start_time_minutes")
        .and_then(|v| v.as_u64())
        .map(|v| v as u32)
        .unwrap_or(default.start_time_minutes);
    let stop_time_minutes = value
        .get("stop_time_minutes")
        .and_then(|v| v.as_u64())
        .map(|v| v as u32)
        .unwrap_or(default.stop_time_minutes);
    let days_of_week = value
        .get("days_of_week")
        .and_then(|v| v.as_array())
        .map(|items| {
            items
                .iter()
                .filter_map(|item| item.as_u64())
                .filter(|day| *day <= 6)
                .map(|day| day as u8)
                .collect::<Vec<_>>()
        })
        .filter(|days| !days.is_empty())
        .unwrap_or_else(|| default.days_of_week.clone());
    UiScheduleConfig {
        enabled,
        start_time_minutes,
        stop_time_minutes,
        days_of_week,
    }
}

fn serialize_schedule_config(config: &UiScheduleConfig) -> Option<String> {
    let mut map = serde_json::Map::new();
    map.insert("enabled".to_string(), serde_json::Value::Bool(config.enabled));
    map.insert("start_time_minutes".to_string(), serde_json::Value::Number(serde_json::Number::from(config.start_time_minutes)));
    map.insert("stop_time_minutes".to_string(), serde_json::Value::Number(serde_json::Number::from(config.stop_time_minutes)));
    map.insert(
        "days_of_week".to_string(),
        serde_json::Value::Array(
            config
                .days_of_week
                .iter()
                .map(|day| serde_json::Value::Number(serde_json::Number::from(*day)))
                .collect(),
        ),
    );
    Some(serde_json::Value::Object(map).to_string())
}

fn format_schedule_time(minutes: u32) -> String {
    let normalized = minutes % (24 * 60);
    format!("{:02}:{:02}", normalized / 60, normalized % 60)
}

fn schedule_days_to_flags(days: &[u8]) -> [bool; 7] {
    let mut flags = [false; 7];
    if days.is_empty() {
        return [true, true, true, true, true, true, true];
    }
    for day in days {
        if (*day as usize) < 7 {
            flags[*day as usize] = true;
        }
    }
    flags
}

fn shift_schedule_minutes(current: u32, delta_minutes: i32) -> u32 {
    let day_minutes = 24 * 60;
    let shifted = (current as i32 + delta_minutes).rem_euclid(day_minutes as i32);
    shifted as u32
}

fn toggle_schedule_day(days: &mut Vec<u8>, day: u8) {
    if day > 6 {
        return;
    }
    if let Some(index) = days.iter().position(|value| *value == day) {
        days.remove(index);
    } else {
        days.push(day);
        days.sort_unstable();
    }
}

fn delete_jobs_for_current_queue(queue_id: i64, statuses: &[&str], delete_all: bool) -> usize {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return 0;
    };
    let _ = repo.init_schema();
    let Ok(jobs) = repo.list_queue_jobs() else {
        return 0;
    };
    let mut removed = 0usize;
    for job in jobs.into_iter().filter(|job| job.queue_id == queue_id) {
        let matches_status = delete_all || statuses.iter().any(|status| *status == job.status);
        if !matches_status {
            continue;
        }
        let _ = flow_core::queue::pause_active_job(&job.id);
        if repo.delete_download_job(&job.id).is_ok() {
            removed += 1;
        }
    }
    removed
}

fn load_queue_ui_state(
    selected_queue_id: i64,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> QueueUiState {
    let db_path = flow_db_path();
    let Some(repo) = SqliteDownloadRepository::open(&db_path).ok() else {
        return QueueUiState {
            queue_labels: vec![],
            queue_ids: vec![],
            rows: vec![],
            row_ids: vec![],
            queue_summary: "DB Error".to_string(),
            active_count: 0,
            total_jobs: 0,
            downloaded_bytes: 0,
            total_bytes: 0,
            active_speed_bytes_per_sec: 0,
        };
    };
    let _ = repo.init_schema();

    let queue_groups = repo.list_queue_groups().unwrap_or_default();
    let groups = queue_groups.iter().map(|g| g.name.clone().into()).collect();
    let group_ids = queue_groups.iter().map(|g| g.id).collect();
    let summary = repo.get_queue_group(selected_queue_id).ok().flatten()
        .map(|g| format!("{} | max {} | stop_on_empty {} | {}", g.name, g.max_concurrent, g.stop_on_empty, if g.active { "active" } else { "paused" }))
        .unwrap_or_else(|| "Queue config unavailable".to_string());
    let Ok(jobs) = repo.list_queue_view_rows() else {
        return QueueUiState {
            queue_labels: groups,
            queue_ids: group_ids,
            rows: vec![],
            row_ids: vec![],
            queue_summary: summary,
            active_count: 0,
            total_jobs: 0,
            downloaded_bytes: 0,
            total_bytes: 0,
            active_speed_bytes_per_sec: 0,
        };
    };
    let category = category_filter.lock().map(|v| *v).unwrap_or(CategoryFilter::All);
    let mut filtered_jobs = jobs
        .into_iter()
        .filter(|row| {
            row.queue_id == selected_queue_id
                && match category {
                    CategoryFilter::All => true,
                    CategoryFilter::Unfinished => !matches!(row.status.as_str(), "Completed" | "Cancelled"),
                    CategoryFilter::Finished => matches!(row.status.as_str(), "Completed" | "Cancelled"),
                }
        })
        .collect::<Vec<_>>();

    let active_sort = sort_state.lock().map(|v| *v).unwrap_or_default();
    sort_queue_rows(&mut filtered_jobs, active_sort);

    let total_jobs = filtered_jobs.len() as i32;
    let active_count = filtered_jobs
        .iter()
        .filter(|row| matches!(classify_download_activity(row.status.as_str()), DownloadActivity::Downloading | DownloadActivity::Queued))
        .count() as i32;
    let downloaded_bytes = filtered_jobs.iter().map(|row| row.downloaded_bytes).sum::<u64>();
    let total_bytes = filtered_jobs.iter().filter_map(|row| row.total_bytes).sum::<u64>();
    let progress_cache = DOWNLOAD_PROGRESS_CACHE.lock().ok();
    let active_speed_bytes_per_sec = filtered_jobs
        .iter()
        .filter_map(|row| estimate_speed_bytes_per_sec(progress_cache.as_deref(), &row.id, row.downloaded_bytes))
        .sum::<u64>();

    if filtered_jobs.is_empty() {
        return QueueUiState {
            queue_labels: groups,
            queue_ids: group_ids,
            rows: vec![],
            row_ids: vec![],
            queue_summary: summary,
            active_count,
            total_jobs,
            downloaded_bytes,
            total_bytes,
            active_speed_bytes_per_sec,
        };
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
            let estimated_speed = estimate_speed_bytes_per_sec(progress_cache.as_deref(), &row.id, row.downloaded_bytes);
            DownloadRow {
                checked: false,
                name: row.file_name.clone().into(),
                category: row.queue_name.clone().into(),
                size: size.into(),
                status: format_status(&row.status, progress_percent).into(),
                speed: format_row_speed(row.status.as_str(), estimated_speed).into(),
                time_left: format_eta(row.status.as_str(), row.total_bytes, row.downloaded_bytes, estimated_speed).into(),
                date_added: format_date_added(row.created_at).into(),
                description: row.last_error.clone().unwrap_or_default().into(),
                progress: progress_percent,
                has_progress: row.total_bytes.unwrap_or(0) > 0 && row.status != "Queued",
                is_error: row.status == "Failed",
            }
        })
        .collect::<Vec<_>>();
    update_progress_cache(&filtered_jobs);
    QueueUiState {
        queue_labels: groups,
        queue_ids: group_ids,
        rows,
        row_ids,
        queue_summary: summary,
        active_count,
        total_jobs,
        downloaded_bytes,
        total_bytes,
        active_speed_bytes_per_sec,
    }
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
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> DownloadsMenuPresentation {
    let state = load_queue_ui_state(queue_id, sort_state, category_filter);
    let snapshot = sync_selection_to_visible_rows(&state.row_ids, selected_download.clone(), checked_downloads.clone());
    let checked_ids = snapshot.selected_ids.iter().cloned().collect::<std::collections::HashSet<_>>();
    let state = apply_checked_rows(state, &checked_ids);
    let selected_queue_index = state
        .queue_ids
        .iter()
        .position(|id| *id == queue_id)
        .map(|idx| idx as i32)
        .unwrap_or(0);
    let selected_index = snapshot
        .main_selected_id
        .as_ref()
        .and_then(|id| state.row_ids.iter().position(|row_id| row_id == id))
        .map(|idx| idx as i32)
        .unwrap_or(-1);
    let action_state = derive_home_action_state(&state.row_ids, &state.rows, &checked_ids, snapshot.main_selected_id.as_ref());
    let registry = derive_home_action_registry(queue_id, action_state, sort_state, category_filter);
    let descriptors = derive_home_action_descriptors(&registry);
    let downloads_menu = derive_downloads_menu_presentation(&descriptors);
    let scheduler_state = load_queue_scheduler_state(queue_id);
    let downloads_menu_for_ui = downloads_menu.clone();
    let _ = weak.upgrade_in_event_loop(move |app| {
        app.set_selected_queue_index(selected_queue_index);
        app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
        app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
        app.set_queue_config_summary(state.queue_summary.into());
        app.set_queue_name_text(load_selected_queue_name(queue_id).into());
        app.set_selected_download_index(selected_index);
        app.set_can_open_selected(descriptors.find(HomeActionId::Open).map(|descriptor| descriptor.enabled).unwrap_or(false));
        app.set_can_open_selected_folder(descriptors.find(HomeActionId::OpenFolder).map(|descriptor| descriptor.enabled).unwrap_or(false));
        app.set_can_delete_selected(descriptors.find(HomeActionId::Delete).map(|descriptor| descriptor.enabled).unwrap_or(false));
        app.set_can_resume_selected(descriptors.find(HomeActionId::Resume).map(|descriptor| descriptor.enabled).unwrap_or(false));
        app.set_can_stop_selected(descriptors.find(HomeActionId::Pause).map(|descriptor| descriptor.enabled).unwrap_or(false));
        app.set_can_move_selected_up(registry.action_state.can_move_up);
        app.set_can_move_selected_down(registry.action_state.can_move_down);
        app.set_can_requeue_selected(descriptors.find(HomeActionId::Requeue).map(|descriptor| descriptor.enabled).unwrap_or(false));
        apply_downloads_menu_presentation(&app, &downloads_menu_for_ui);
        app.set_queue_stop_on_empty(scheduler_state.stop_on_empty);
        app.set_queue_schedule_enabled(scheduler_state.enabled);
        app.set_queue_schedule_start(scheduler_state.start_text.into());
        app.set_queue_schedule_stop(scheduler_state.stop_text.into());
        app.set_footer_active_count(state.active_count);
        app.set_footer_speed_text(
            if state.active_speed_bytes_per_sec > 0 {
                format!("{}/s", format_bytes(state.active_speed_bytes_per_sec))
            } else {
                format_bytes(state.downloaded_bytes)
            }
            .into(),
        );
        app.set_footer_total_text(
            if state.total_bytes > 0 {
                format!("{} / {}", state.total_jobs, format_bytes(state.total_bytes))
            } else {
                state.total_jobs.to_string()
            }
            .into(),
        );
        app.set_queue_day_sun(scheduler_state.days[0]);
        app.set_queue_day_mon(scheduler_state.days[1]);
        app.set_queue_day_tue(scheduler_state.days[2]);
        app.set_queue_day_wed(scheduler_state.days[3]);
        app.set_queue_day_thu(scheduler_state.days[4]);
        app.set_queue_day_fri(scheduler_state.days[5]);
        app.set_queue_day_sat(scheduler_state.days[6]);
    });
    downloads_menu
}

fn apply_checked_rows(
    mut state: QueueUiState,
    checked_ids: &std::collections::HashSet<String>,
) -> QueueUiState {
    for (idx, row) in state.rows.iter_mut().enumerate() {
        if let Some(id) = state.row_ids.get(idx) {
            row.checked = checked_ids.contains(id);
        }
    }
    state
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

fn format_speed(bytes_per_sec: u64) -> String {
    format!("{}/s", format_bytes(bytes_per_sec))
}

fn format_duration_compact(seconds: u64) -> String {
    if seconds < 60 {
        format!("{}s", seconds)
    } else if seconds < 3600 {
        format!("{}m", seconds.div_ceil(60))
    } else if seconds < 86_400 {
        format!("{}h {}m", seconds / 3600, (seconds % 3600).div_ceil(60))
    } else {
        format!("{}d {}h", seconds / 86_400, (seconds % 86_400) / 3600)
    }
}

fn format_row_speed(status: &str, speed_bytes_per_sec: Option<u64>) -> String {
    if status != "Downloading" {
        return "--".to_string();
    }
    speed_bytes_per_sec
        .filter(|value| *value > 0)
        .map(format_speed)
        .unwrap_or_else(|| "calculating".to_string())
}

fn format_eta(
    status: &str,
    total_bytes: Option<u64>,
    downloaded_bytes: u64,
    speed_bytes_per_sec: Option<u64>,
) -> String {
    if status != "Downloading" {
        return "--".to_string();
    }
    let Some(total_bytes) = total_bytes.filter(|value| *value > downloaded_bytes) else {
        return "--".to_string();
    };
    let Some(speed_bytes_per_sec) = speed_bytes_per_sec.filter(|value| *value > 0) else {
        return "estimating".to_string();
    };
    format_duration_compact((total_bytes - downloaded_bytes).div_ceil(speed_bytes_per_sec))
}

fn estimate_speed_bytes_per_sec(
    cache: Option<&std::collections::HashMap<String, ProgressSample>>,
    id: &str,
    downloaded_bytes: u64,
) -> Option<u64> {
    let sample = cache?.get(id)?;
    if downloaded_bytes <= sample.downloaded_bytes {
        return None;
    }
    let elapsed = SystemTime::now().duration_since(sample.observed_at).ok()?;
    if elapsed < Duration::from_millis(400) || elapsed > Duration::from_secs(20) {
        return None;
    }
    let delta_bytes = downloaded_bytes - sample.downloaded_bytes;
    let bytes_per_sec = (delta_bytes as f64 / elapsed.as_secs_f64()).round() as u64;
    (bytes_per_sec > 0).then_some(bytes_per_sec)
}

fn update_progress_cache(rows: &[flow_core::QueueViewRow]) {
    let now = SystemTime::now();
    if let Ok(mut cache) = DOWNLOAD_PROGRESS_CACHE.lock() {
        let visible_ids = rows.iter().map(|row| row.id.clone()).collect::<std::collections::HashSet<_>>();
        cache.retain(|id, _| visible_ids.contains(id));
        for row in rows {
            cache.insert(
                row.id.clone(),
                ProgressSample {
                    downloaded_bytes: row.downloaded_bytes,
                    observed_at: now,
                },
            );
        }
    }
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
    app.on_queue_schedule_toggle_enabled({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            let mut schedule = parse_schedule_config(group.schedule_json.as_deref());
            schedule.enabled = !schedule.enabled;
            group.schedule_json = serialize_schedule_config(&schedule);
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_schedule_start_minus({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            let mut schedule = parse_schedule_config(group.schedule_json.as_deref());
            schedule.start_time_minutes = shift_schedule_minutes(schedule.start_time_minutes, -30);
            group.schedule_json = serialize_schedule_config(&schedule);
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_schedule_start_plus({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            let mut schedule = parse_schedule_config(group.schedule_json.as_deref());
            schedule.start_time_minutes = shift_schedule_minutes(schedule.start_time_minutes, 30);
            group.schedule_json = serialize_schedule_config(&schedule);
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_schedule_stop_minus({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            let mut schedule = parse_schedule_config(group.schedule_json.as_deref());
            schedule.stop_time_minutes = shift_schedule_minutes(schedule.stop_time_minutes, -30);
            group.schedule_json = serialize_schedule_config(&schedule);
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_schedule_stop_plus({
        let selected_queue = Arc::clone(&selected_queue);
        move || mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            let mut schedule = parse_schedule_config(group.schedule_json.as_deref());
            schedule.stop_time_minutes = shift_schedule_minutes(schedule.stop_time_minutes, 30);
            group.schedule_json = serialize_schedule_config(&schedule);
            let _ = repo.upsert_queue_group(&group);
        })
    });
    app.on_queue_schedule_toggle_day({
        let selected_queue = Arc::clone(&selected_queue);
        move |day| mutate_selected_queue(selected_queue.clone(), |repo, mut group| {
            let mut schedule = parse_schedule_config(group.schedule_json.as_deref());
            toggle_schedule_day(&mut schedule.days_of_week, day as u8);
            group.schedule_json = serialize_schedule_config(&schedule);
            let _ = repo.upsert_queue_group(&group);
        })
    });
}

fn current_home_action_state(
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> HomeActionState {
    let state = load_queue_ui_state(queue_id, sort_state, category_filter);
    let snapshot = sync_selection_to_visible_rows(&state.row_ids, selected_download, checked_downloads);
    let checked_ids = snapshot.selected_ids.iter().cloned().collect::<std::collections::HashSet<_>>();
    derive_home_action_state(&state.row_ids, &state.rows, &checked_ids, snapshot.main_selected_id.as_ref())
}

fn current_home_action_registry(
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> HomeActionRegistry {
    let action_state = current_home_action_state(queue_id, selected_download, checked_downloads, sort_state, category_filter);
    derive_home_action_registry(queue_id, action_state, sort_state, category_filter)
}

fn execute_downloads_menu_command(
    command_id: &str,
    target_index: i32,
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
    weak: &slint::Weak<MainWindow>,
) {
    let registry = current_home_action_registry(
        queue_id,
        selected_download.clone(),
        checked_downloads.clone(),
        sort_state,
        category_filter,
    );


    match command_id {
        "edit" => match execute_open_edit_dialog(&registry, queue_id, sort_state, category_filter) {
            Ok(payload) => open_edit_download_dialog(
                payload.id,
                payload.file_name,
                payload.output_dir,
                payload.priority,
                queue_id,
                selected_download,
                checked_downloads,
                sort_state.clone(),
                category_filter.clone(),
                weak.clone(),
            ),
            Err(message) => set_status(weak, message),
        },
        "restart-download" => {
            let result = execute_restart_selected(&registry);
            refresh_queue_ui(weak, queue_id, selected_download, checked_downloads, sort_state, category_filter);
            set_status(weak, &result.status_message);
        }
        "properties" => match execute_show_selected_properties(&registry, queue_id, sort_state, category_filter) {
            Ok(payload) => {
                let _ = weak.upgrade_in_event_loop(move |app| {
                    app.set_properties_summary(payload.summary.into());
                    app.set_show_properties_dialog(true);
                });
                set_status(weak, "Showing selected download properties");
            }
            Err(message) => set_status(weak, message),
        },
        "open-or-properties" => match execute_open_file_or_properties(&registry, queue_id, sort_state, category_filter) {
            Ok(payload) => {
                let _ = weak.upgrade_in_event_loop(move |app| {
                    app.set_properties_summary(payload.summary.into());
                    app.set_show_properties_dialog(true);
                });
                set_status(weak, "Showing selected download properties");
            }
            Err("open-file") => {
                let ids = effective_checked_ids(queue_id, checked_downloads.clone(), selected_download.clone(), sort_state, category_filter);
                let open_result = open_multiple_selected_paths(&ids, false);
                let result = selected_open_result(&open_result, false);
                if result.changed_count == 0 && open_result.missing_count > 0 {
                    if let Ok(payload) = execute_show_selected_properties(&registry, queue_id, sort_state, category_filter) {
                        let weak = weak.clone();
                        let _ = weak.upgrade_in_event_loop(move |app| {
                            app.set_properties_summary(payload.summary.into());
                            app.set_show_properties_dialog(true);
                        });
                    }
                }
                set_status(weak, &result.status_message);
            }
            Err(message) => set_status(weak, message),
        },
        "file-checksum" => match execute_open_file_checksum_dialog(&registry) {
            Ok(payload) => open_file_checksum_dialog(payload, weak.clone()),
            Err(message) => set_status(weak, message),
        },
        "copy-links" => set_status(weak, &execute_copy_selected_links(&registry)),
        "copy-as-curl" => set_status(weak, &execute_copy_as_curl(&registry).status_message),
        "move-to-queue" => match execute_move_to_queue(&registry, target_index) {
            Ok(result) => {
                refresh_queue_ui(weak, queue_id, selected_download, checked_downloads, sort_state, category_filter);
                set_status(weak, &format!("Moved {} download(s) to queue '{}'", result.moved_count, result.target_queue_name));
            }
            Err(message) => set_status(weak, message),
        },
        "move-to-category" => match execute_move_to_category(&registry, target_index) {
            Ok(result) => {
                refresh_queue_ui(weak, queue_id, selected_download, checked_downloads, sort_state, category_filter);
                set_status(weak, &format!("Moved {} download(s) to category '{}'", result.moved_count, result.category));
            }
            Err(message) => set_status(weak, message),
        },
        _ => set_status(weak, "Unsupported downloads menu command"),
    }
}

fn wire_download_toolbar_actions(
    app: &MainWindow,
    selected_queue: Arc<Mutex<i64>>,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: Arc<Mutex<SortState>>,
    category_filter: Arc<Mutex<CategoryFilter>>,
) {
    app.on_add_url({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            show_add_url_dialog(
                selected_queue.clone(),
                selected_download.clone(),
                checked_downloads.clone(),
                sort_state.clone(),
                category_filter.clone(),
                weak.clone(),
            );
        }
    });
    app.on_resume_selected({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            let status_message = execute_resume_selected(&registry, queue_id);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &status_message);
        }
    });
    app.on_stop_selected({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            let status_message = execute_pause_selected(&registry, queue_id);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &status_message);
        }
    });
    app.on_stop_all({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let result = stop_all_result(pause_all_jobs(queue_id));
            if result.changed_count > 0 {
                if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
                    let _ = repo.init_schema();
                    let _ = repo.log_queue_event(queue_id, "queue_paused_all", Some(&format!("{{\"count\":{}}}", result.changed_count)));
                }
            }
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &result.status_message);
        }
    });
    app.on_request_delete_selected({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            if registry.action_state.selected_ids.is_empty() {
                set_status(&weak, "No downloads selected. Check one or more rows first.");
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
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            let status_message = execute_delete_selected(&registry);
            clear_selection_after_delete(&registry.action_state.selected_ids, checked_downloads.clone(), selected_download.clone());
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            let _ = weak.upgrade_in_event_loop(|app| app.set_show_delete_confirm(false));
            set_status(&weak, &status_message);
        }
    });
    app.on_delete_selected({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            let status_message = execute_delete_selected(&registry);
            clear_selection_after_delete(&registry.action_state.selected_ids, checked_downloads.clone(), selected_download.clone());
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &status_message);
        }
    });
    app.on_open_selected({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            execute_downloads_menu_command(
                "open-or-properties",
                -1,
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
                &weak,
            );
        }
    });
    app.on_open_selected_folder({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let ids = effective_checked_ids(queue_id, checked_downloads.clone(), selected_download.clone(), &sort_state, &category_filter);
            let open_result = open_multiple_selected_paths(&ids, true);
            let result = selected_open_result(&open_result, true);
            set_status(&weak, &result.status_message);
        }
    });
    app.on_copy_selected_links({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            set_status(&weak, &execute_copy_selected_links(&registry));
        }
    });
    app.on_copy_selected_as_curl({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            set_status(&weak, &execute_copy_as_curl(&registry).status_message);
        }
    });
    app.on_show_selected_properties({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            let Ok(payload) = execute_show_selected_properties(&registry, queue_id, &sort_state, &category_filter) else {
                set_status(&weak, "Unable to load selected download properties");
                return;
            };
            let _ = weak.upgrade_in_event_loop(move |app| {
                app.set_properties_summary(payload.summary.into());
                app.set_show_properties_dialog(true);
            });
            set_status(&weak, "Showing selected download properties");
        }
    });
    app.on_close_properties_dialog({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| app.set_show_properties_dialog(false));
        }
    });
    app.on_edit_selected_download({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            match execute_open_edit_dialog(&registry, queue_id, &sort_state, &category_filter) {
                Ok(payload) => open_edit_download_dialog(payload.id, payload.file_name, payload.output_dir, payload.priority, queue_id, selected_download.clone(), checked_downloads.clone(), sort_state.clone(), category_filter.clone(), weak.clone()),
                Err(message) => set_status(&weak, message),
            }
        }
    });
    app.on_show_file_checksum({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = current_home_action_registry(
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
            );
            match execute_open_file_checksum_dialog(&registry) {
                Ok(payload) => open_file_checksum_dialog(payload, weak.clone()),
                Err(message) => set_status(&weak, message),
            }
        }
    });
    app.on_close_file_checksum_dialog({
        let weak = app.as_weak();
        move || {
            let _ = weak.upgrade_in_event_loop(|app| app.set_show_file_checksum_dialog(false));
        }
    });
    app.on_restart_selected_downloads({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move || {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = derive_home_action_registry(
                queue_id,
                current_home_action_state(queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter),
                &sort_state,
                &category_filter,
            );
            let result = execute_restart_selected(&registry);
            refresh_queue_ui(&weak, queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter);
            set_status(&weak, &result.status_message);
        }
    });
    app.on_move_download_up({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            let result = mutate_selected_job_with_status(selected_download.clone(), "Moved up", "No download selected to move up", |repo, id| {
                let Some(before) = repo.get_queue_job(id).ok().flatten() else { return false; };
                let _ = repo.reorder_queue_job(id, -1);
                let Some(after) = repo.get_queue_job(id).ok().flatten() else { return false; };
                let changed = before.queue_order != after.queue_order;
                if changed {
                    let _ = repo.log_queue_event(before.queue_id, "job_moved", Some(&format!("{{\"id\":\"{}\",\"direction\":\"up\"}}", id)));
                }
                changed
            });
            set_status(&weak, &result.status_message);
        }
    });
    app.on_move_download_down({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            let result = mutate_selected_job_with_status(selected_download.clone(), "Moved down", "No download selected to move down", |repo, id| {
                let Some(before) = repo.get_queue_job(id).ok().flatten() else { return false; };
                let _ = repo.reorder_queue_job(id, 1);
                let Some(after) = repo.get_queue_job(id).ok().flatten() else { return false; };
                let changed = before.queue_order != after.queue_order;
                if changed {
                    let _ = repo.log_queue_event(before.queue_id, "job_moved", Some(&format!("{{\"id\":\"{}\",\"direction\":\"down\"}}", id)));
                }
                changed
            });
            set_status(&weak, &result.status_message);
        }
    });
    app.on_requeue_download({
        let selected_download = Arc::clone(&selected_download);
        let weak = app.as_weak();
        move || {
            let result = mutate_selected_job_with_status(selected_download.clone(), "Requeued", "No download selected to requeue", |repo, id| {
                let Some(before) = repo.get_queue_job(id).ok().flatten() else { return false; };
                let changed = before.status != "Queued";
                let _ = repo.update_queue_job_status(id, "Queued");
                if changed {
                    let _ = repo.log_queue_event(before.queue_id, "job_requeued", Some(&format!("{{\"id\":\"{}\"}}", id)));
                }
                changed
            });
            set_status(&weak, &result.status_message);
        }
    });
    app.on_downloads_menu_open_submenu({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move |parent_index| {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            let registry = derive_home_action_registry(
                queue_id,
                current_home_action_state(queue_id, selected_download.clone(), checked_downloads.clone(), &sort_state, &category_filter),
                &sort_state,
                &category_filter,
            );
            let descriptors = derive_home_action_descriptors(&registry);
            let presentation = derive_downloads_menu_presentation(&descriptors);
            let submenu_rows = map_submenu_rows(&presentation, parent_index);
            let _ = weak.upgrade_in_event_loop(move |app| {
                app.set_downloads_submenu_rows(ModelRc::new(VecModel::from(submenu_rows)));
            });
        }
    });
    app.on_downloads_menu_command({
        let selected_queue = Arc::clone(&selected_queue);
        let selected_download = Arc::clone(&selected_download);
        let checked_downloads = Arc::clone(&checked_downloads);
        let sort_state = Arc::clone(&sort_state);
        let category_filter = Arc::clone(&category_filter);
        let weak = app.as_weak();
        move |command_id, target_index| {
            let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
            execute_downloads_menu_command(
                command_id.as_str(),
                target_index,
                queue_id,
                selected_download.clone(),
                checked_downloads.clone(),
                &sort_state,
                &category_filter,
                &weak,
            );
        }
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

fn show_add_url_dialog(
    selected_queue: Arc<Mutex<i64>>,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: Arc<Mutex<SortState>>,
    category_filter: Arc<Mutex<CategoryFilter>>,
    weak: slint::Weak<MainWindow>,
) {
    let queue_id = selected_queue.lock().map(|v| *v).unwrap_or(0);
    let queue_state = load_queue_ui_state(queue_id, &sort_state, &category_filter);
    let selected_queue_index = queue_state
        .queue_ids
        .iter()
        .position(|id| *id == queue_id)
        .unwrap_or(0) as i32;

    if let Ok(dialog) = AddUrlDialog::new() {
        let default_folder = resolve_default_download_folder();
        let default_file = infer_file_name("");
        dialog.set_url("".into());
        dialog.set_file_name("".into());
        dialog.set_output_dir(default_folder.clone().into());
        dialog.set_category("General".into());
        dialog.set_resolved_file_name(default_file.into());
        dialog.set_resolved_output_dir(default_folder.clone().into());
        dialog.set_category_hint("General".into());
        dialog.set_dialog_hint("Paste a direct HTTP or HTTPS link. Flow will infer the file name and category automatically.".into());
        dialog.set_url_valid(true);
        dialog.set_queue_options(ModelRc::new(VecModel::from(queue_state.queue_labels.clone())));
        dialog.set_queue_index(selected_queue_index);

        let dialog_weak = dialog.as_weak();
        dialog.on_choose_folder(move || {
            if let Some(folder) = open_folder_picker() {
                if let Some(dlg) = dialog_weak.upgrade() {
                    dlg.set_output_dir(folder.clone().into());
                    dlg.set_resolved_output_dir(folder.clone().into());
                    render_add_url_preview(&dlg, dlg.get_url().as_str(), dlg.get_file_name().as_str(), folder.as_str(), dlg.get_category().as_str());
                }
            }
        });

        let dialog_weak = dialog.as_weak();
        dialog.on_cancel(move || {
            if let Some(dlg) = dialog_weak.upgrade() {
                dlg.hide().ok();
            }
        });

        let dialog_weak = dialog.as_weak();
        dialog.on_preview_update(move |url, file_name, output_dir, category| {
            if let Some(dlg) = dialog_weak.upgrade() {
                render_add_url_preview(&dlg, url.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str());
            }
        });

        let queue_ids_for_later = queue_state.queue_ids.clone();
        let dialog_weak = dialog.as_weak();
        let weak_later = weak.clone();
        let selected_queue_for_later = Arc::clone(&selected_queue);
        let selected_download_for_later = Arc::clone(&selected_download);
        let checked_downloads_for_later = Arc::clone(&checked_downloads);
        let sort_state_for_later = Arc::clone(&sort_state);
        let category_filter_for_later = Arc::clone(&category_filter);
        dialog.on_download_later(move |url, file_name, output_dir, queue_index, category| {
            let selected_queue_id = queue_ids_for_later
                .get(queue_index as usize)
                .copied()
                .unwrap_or(queue_id);
            let submit = match prepare_manual_download_submission(url.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str()) {
                Ok(submit) => submit,
                Err(message) => {
                    set_status(&weak_later, &message);
                    if let Some(dlg) = dialog_weak.upgrade() {
                        render_add_url_error(&dlg, &message, url.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str());
                    }
                    return;
                }
            };
            match enqueue_manual_url(selected_queue_id, submit.url.as_str(), submit.file_name.as_str(), submit.output_dir.as_str(), false, submit.category.as_str()) {
                Ok(created_name) => {
                    if let Ok(mut selected) = selected_queue_for_later.lock() {
                        *selected = selected_queue_id;
                    }
                    refresh_queue_ui(
                        &weak_later,
                        selected_queue_id,
                        selected_download_for_later.clone(),
                        checked_downloads_for_later.clone(),
                        &sort_state_for_later,
                        &category_filter_for_later,
                    );
                    set_status(&weak_later, &format!("Queued {created_name} in {}", submit.category));
                    if let Some(dlg) = dialog_weak.upgrade() {
                        dlg.hide().ok();
                    }
                }
                Err(message) => set_status(&weak_later, &message),
            }
        });

        let queue_ids_for_start = queue_state.queue_ids.clone();
        let dialog_weak = dialog.as_weak();
        let weak_now = weak.clone();
        let selected_queue_for_start = Arc::clone(&selected_queue);
        let selected_download_for_start = Arc::clone(&selected_download);
        let checked_downloads_for_start = Arc::clone(&checked_downloads);
        let sort_state_for_start = Arc::clone(&sort_state);
        let category_filter_for_start = Arc::clone(&category_filter);
        dialog.on_start_now(move |url, file_name, output_dir, queue_index, category| {
            let selected_queue_id = queue_ids_for_start
                .get(queue_index as usize)
                .copied()
                .unwrap_or(queue_id);
            let submit = match prepare_manual_download_submission(url.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str()) {
                Ok(submit) => submit,
                Err(message) => {
                    set_status(&weak_now, &message);
                    if let Some(dlg) = dialog_weak.upgrade() {
                        render_add_url_error(&dlg, &message, url.as_str(), file_name.as_str(), output_dir.as_str(), category.as_str());
                    }
                    return;
                }
            };
            match enqueue_manual_url(selected_queue_id, submit.url.as_str(), submit.file_name.as_str(), submit.output_dir.as_str(), true, submit.category.as_str()) {
                Ok(created_name) => {
                    if let Ok(mut selected) = selected_queue_for_start.lock() {
                        *selected = selected_queue_id;
                    }
                    refresh_queue_ui(
                        &weak_now,
                        selected_queue_id,
                        selected_download_for_start.clone(),
                        checked_downloads_for_start.clone(),
                        &sort_state_for_start,
                        &category_filter_for_start,
                    );
                    set_status(&weak_now, &format!("Queued {created_name} to start in {}", submit.category));
                    if let Some(dlg) = dialog_weak.upgrade() {
                        dlg.hide().ok();
                    }
                }
                Err(message) => set_status(&weak_now, &message),
            }
        });

        let _ = dialog.show();
    } else {
        set_status(&weak, "Unable to open Add URL dialog");
    }
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

fn enqueue_manual_url(queue_id: i64, url: &str, file_name: &str, output_dir: &str, start_now: bool, category: &str) -> Result<String, String> {
    let url = url.trim();
    if url.is_empty() {
        return Err("Download URL cannot be empty".to_string());
    }
    if !is_http_url(url) {
        return Err("Only HTTP and HTTPS URLs are supported in this dialog".to_string());
    }
    let output_dir = if output_dir.trim().is_empty() {
        resolve_default_download_folder()
    } else {
        output_dir.trim().to_string()
    };
    let file_name = if file_name.trim().is_empty() {
        infer_file_name(url)
    } else {
        file_name.trim().to_string()
    };
    let millis = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0);
    let settings = load_settings(&flow_data_dir().join("settings.json"));
    let per_host = flow_core::per_host_for_url(&settings, url);
    let effective_threads = per_host
        .and_then(|entry| entry.thread_count)
        .unwrap_or(settings.thread_count)
        .max(1);
    let job = QueueJobRecord {
        id: format!("manual-{millis}"),
        queue_id,
        url: url.to_string(),
        output_dir,
        file_name: file_name.clone(),
        category: category.to_string(),
        connections: effective_threads,
        expected_sha256_hex: None,
        headers_json: Some(serde_json::json!({ "category": category }).to_string()),
        referrer: None,
        cookies: None,
        user_agent: per_host.and_then(|entry| entry.user_agent.clone()),
        username: per_host.and_then(|entry| entry.username.clone()),
        password: per_host.and_then(|entry| entry.password.clone()),
        proxy_url: None,
        proxy_username: None,
        proxy_password: None,
        status: if start_now { "Queued" } else { "Paused" }.to_string(),
        priority: if start_now { 1 } else { 0 },
        queue_order: millis as i64,
        attempt_count: 0,
        last_error: None,
    };
    let repo = SqliteDownloadRepository::open(&flow_db_path())
        .map_err(|_| "Queue database is not available".to_string())?;
    let _ = repo.init_schema();
    repo.upsert_queue_job(&job)
        .map_err(|_| "Unable to save the download job".to_string())?;
    Ok(file_name)
}

fn open_edit_download_dialog(
    job_id: String,
    file_name: String,
    output_dir: String,
    priority: i32,
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: Arc<Mutex<SortState>>,
    category_filter: Arc<Mutex<CategoryFilter>>,
    weak: slint::Weak<MainWindow>,
) {
    if let Ok(dialog) = EditDownloadDialog::new() {
        dialog.set_file_name(file_name.into());
        dialog.set_output_dir(output_dir.into());
        dialog.set_priority(priority);

        let dialog_weak = dialog.as_weak();
        dialog.on_cancel(move || {
            if let Some(dlg) = dialog_weak.upgrade() {
                let _ = dlg.hide();
            }
        });

        let dialog_weak = dialog.as_weak();
        dialog.on_choose_folder(move || {
            if let Some(folder) = open_folder_picker() {
                if let Some(dlg) = dialog_weak.upgrade() {
                    dlg.set_output_dir(folder.into());
                }
            }
        });

        let dialog_weak = dialog.as_weak();
        dialog.on_set_priority(move |value| {
            if let Some(dlg) = dialog_weak.upgrade() {
                dlg.set_priority(value);
            }
        });

        let weak_save = weak.clone();
        let dialog_weak = dialog.as_weak();
        let selected_download_for_save = Arc::clone(&selected_download);
        let checked_downloads_for_save = Arc::clone(&checked_downloads);
        let sort_state_for_save = Arc::clone(&sort_state);
        let category_filter_for_save = Arc::clone(&category_filter);
        dialog.on_save(move |file_name, output_dir, priority| {
            let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
                set_status(&weak_save, "Queue database is not available");
                return;
            };
            let _ = repo.init_schema();
            let Some(mut job) = repo.get_queue_job(job_id.as_str()).ok().flatten() else {
                set_status(&weak_save, "Unable to load selected download");
                return;
            };
            job.file_name = file_name.trim().to_string();
            job.output_dir = if output_dir.trim().is_empty() { resolve_default_download_folder() } else { output_dir.trim().to_string() };
            job.priority = priority as i64;
            if repo.upsert_queue_job(&job).is_ok() {
                refresh_queue_ui(&weak_save, queue_id, selected_download_for_save.clone(), checked_downloads_for_save.clone(), &sort_state_for_save, &category_filter_for_save);
                set_status(&weak_save, "Updated selected download");
                if let Some(dlg) = dialog_weak.upgrade() {
                    let _ = dlg.hide();
                }
            } else {
                set_status(&weak_save, "Unable to save selected download changes");
            }
        });

        let _ = dialog.show();
    } else {
        set_status(&weak, "Unable to open Edit Download dialog");
    }
}

fn checksum_file(path: &std::path::Path) -> Result<String, String> {
    let mut file = std::fs::File::open(path).map_err(|_| "File missing".to_string())?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 8192];
    loop {
        let read = std::io::Read::read(&mut file, &mut buffer).map_err(|_| "Read error".to_string())?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn checksum_status_line(done: usize, total: usize, matched: usize, mismatched: usize, failed: usize) -> String {
    if done < total {
        format!("Checking {} / {}...", done, total)
    } else {
        format!("Done: {} match, {} mismatch, {} error", matched, mismatched, failed)
    }
}

fn open_file_checksum_dialog(payload: FileChecksumDialogPayload, weak: slint::Weak<MainWindow>) {
    if let Ok(dialog) = FileChecksumDialog::new() {
        let mut rows = payload
            .items
            .iter()
            .map(|item| FileChecksumRow {
                file_name: item.file_name.clone().into(),
                expected: item.expected_sha256_hex.clone().unwrap_or_else(|| "Not set".to_string()).into(),
                actual: "--".into(),
                result: "Pending".into(),
            })
            .collect::<Vec<_>>();

        dialog.set_rows(ModelRc::new(VecModel::from(rows.clone())));
        dialog.set_status_text(format!("Preparing {} item(s)...", rows.len()).into());

        let mut done = 0usize;
        let mut matched = 0usize;
        let mut mismatched = 0usize;
        let mut failed = 0usize;

        for (index, item) in payload.items.iter().enumerate() {
            rows[index].result = "Checking".into();
            dialog.set_rows(ModelRc::new(VecModel::from(rows.clone())));
            dialog.set_status_text(checksum_status_line(done, payload.items.len(), matched, mismatched, failed).into());

            let expected = item.expected_sha256_hex.as_ref().map(|v| v.trim().to_lowercase()).filter(|v| !v.is_empty());
            let path = std::path::Path::new(&item.output_path);
            if expected.is_none() {
                rows[index].actual = "--".into();
                rows[index].result = "No hash".into();
                failed += 1;
            } else {
                match checksum_file(path) {
                    Ok(actual) => {
                        rows[index].actual = actual.clone().into();
                        if expected.as_deref() == Some(actual.as_str()) {
                            rows[index].result = "Match".into();
                            matched += 1;
                        } else {
                            rows[index].result = "Mismatch".into();
                            mismatched += 1;
                        }
                    }
                    Err(reason) => {
                        rows[index].actual = reason.into();
                        rows[index].result = "Error".into();
                        failed += 1;
                    }
                }
            }
            done += 1;
            dialog.set_rows(ModelRc::new(VecModel::from(rows.clone())));
            dialog.set_status_text(checksum_status_line(done, payload.items.len(), matched, mismatched, failed).into());
        }

        set_status(
            &weak,
            &format!(
                "Checksum complete: {} match, {} mismatch, {} error",
                matched, mismatched, failed
            ),
        );

        let dialog_weak = dialog.as_weak();
        dialog.on_close_dialog(move || {
            if let Some(dlg) = dialog_weak.upgrade() {
                let _ = dlg.hide();
            }
        });
        let _ = dialog.show();
    } else {
        set_status(&weak, "Unable to open File Checksum dialog");
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

fn normalize_category_input(file_name: &str, url: &str, category: &str) -> String {
    let category = category.trim();
    if category.is_empty() || category.eq_ignore_ascii_case("general") {
        infer_category_from_name_or_url(file_name, url)
    } else {
        category.to_string()
    }
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
    let (urls, mut invalid) = parse_batch_urls(urls_text);
    let mut queued = 0usize;
    for url in &urls {
        match enqueue_manual_url(queue_id, url, "", output_dir, start_now, category) {
            Ok(_) => queued += 1,
            Err(_) => invalid += 1,
        }
    }
    (queued, invalid)
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
        let weak_status = weak.clone();
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
            set_status(&weak_status, "New per-host rule created. Fill the host pattern, then save.");
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
            let host = normalize_host_pattern(host.as_str());
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
                let current_slot = if items.is_empty() { None } else { Some((*idx).min(items.len().saturating_sub(1))) };
                let duplicate = items.iter().enumerate().any(|(entry_idx, item)| {
                    item.host.eq_ignore_ascii_case(host.as_str()) && Some(entry_idx) != current_slot
                });
                if duplicate {
                    set_status(&weak_status, "A rule for this host pattern already exists");
                    return;
                }
                if items.is_empty() {
                    items.push(PerHostSettings {
                        host: host.clone(),
                        username: none_if_empty(username.as_str()),
                        password: none_if_empty(password.as_str()),
                        user_agent: none_if_empty(user_agent.as_str()),
                        thread_count,
                    });
                } else {
                    let index = current_slot.unwrap_or(0);
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
            set_status(&weak_status, &format!("Per-host rule saved for {host}"));
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
                    let removed_host = items.get(remove_at).map(|item| item.host.clone()).unwrap_or_default();
                    items.remove(remove_at);
                    if *idx > 0 {
                        *idx -= 1;
                    }
                    save_per_host_entries(items.as_slice());
                    if removed_host.is_empty() {
                        set_status(&weak_status, "Per-host entry deleted");
                    } else {
                        set_status(&weak_status, &format!("Removed host rule {removed_host}"));
                    }
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
    let has_entry = total > 0;
    let can_go_prev = has_entry && idx > 0;
    let can_go_next = has_entry && idx + 1 < total;
    let can_delete_entry = has_entry;
    let (host, username, password, user_agent, thread_count_text, summary) = if total == 0 {
        (
            String::new(),
            String::new(),
            String::new(),
            String::new(),
            String::new(),
            "No host overrides configured".to_string(),
        )
    } else {
        let item = &items[idx];
        (
            item.host.clone(),
            item.username.clone().unwrap_or_default(),
            item.password.clone().unwrap_or_default(),
            item.user_agent.clone().unwrap_or_default(),
            item.thread_count.map(|v| v.to_string()).unwrap_or_default(),
            format!(
                "Rule for {}{}{}",
                if item.host.trim().is_empty() { "(unsaved host pattern)" } else { item.host.trim() },
                if item.thread_count.is_some() { " • custom threads" } else { "" },
                if item.user_agent.as_ref().map(|v| !v.trim().is_empty()).unwrap_or(false) { " • custom UA" } else { "" }
            ),
        )
    };
    if let Ok(mut write_idx) = index.lock() {
        *write_idx = idx;
    }
    dialog.set_entry_position(format!("{}/{}", if total == 0 { 0 } else { idx + 1 }, total).into());
    dialog.set_dialog_summary(summary.into());
    dialog.set_has_entry(has_entry);
    dialog.set_can_go_prev(can_go_prev);
    dialog.set_can_go_next(can_go_next);
    dialog.set_can_delete_entry(can_delete_entry);
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

fn normalize_host_pattern(value: &str) -> String {
    value.trim().trim_start_matches("http://").trim_start_matches("https://").trim_end_matches('/').to_ascii_lowercase()
}

fn none_if_empty(value: &str) -> Option<String> {
    let value = value.trim();
    if value.is_empty() {
        None
    } else {
        Some(value.to_string())
    }
}

fn open_external(url: &str) {
    let _ = std::process::Command::new("explorer.exe").arg(url).spawn();
}

fn build_about_message() -> String {
    format!(
        "Flow Download Manager {} | Desktop UI powered by Rust + Slint | Inspired by IDM-style desktop workflows",
        env!("CARGO_PKG_VERSION")
    )
}

fn validate_proxy_settings(settings: &flow_core::FlowSettings) -> String {
    match settings.proxy_mode {
        0 => "Proxy mode: Direct connection is active".to_string(),
        1 => "Proxy mode: System proxy settings will be used".to_string(),
        2 => {
            let manual = &settings.proxy.manual;
            if manual.host.trim().is_empty() {
                return "Proxy test failed: manual proxy host is empty".to_string();
            }
            if manual.port == 0 {
                return "Proxy test failed: manual proxy port must be greater than 0".to_string();
            }
            format!(
                "Proxy ready: {}://{}:{}",
                manual.scheme,
                manual.host.trim(),
                manual.port
            )
        }
        _ => "Proxy mode is not recognized by the desktop UI yet".to_string(),
    }
}

fn rename_queue_with_desktop_style(queue_id: i64, requested_name: &str) -> Result<String, String> {
    if queue_id == 0 {
        return Err("Main queue is locked from quick rename".to_string());
    }
    let next_name = requested_name.trim();
    if next_name.is_empty() {
        return Err("Queue name cannot be empty".to_string());
    }
    let repo = SqliteDownloadRepository::open(&flow_db_path())
        .map_err(|_| "Queue database is not available".to_string())?;
    let _ = repo.init_schema();
    let mut group = repo
        .get_queue_group(queue_id)
        .map_err(|_| "Unable to read selected queue".to_string())?
        .ok_or_else(|| "Selected queue was not found".to_string())?;
    group.name = next_name.to_string();
    repo.upsert_queue_group(&group)
        .map_err(|_| "Unable to save renamed queue".to_string())?;
    Ok(format!("Queue renamed to {next_name}"))
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

fn resolve_default_download_folder() -> String {
    load_settings(&flow_data_dir().join("settings.json"))
        .default_download_folder
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(default_downloads_folder)
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
