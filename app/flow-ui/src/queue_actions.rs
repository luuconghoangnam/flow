use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use flow_core::{flow_db_path, pause_active_job, DownloadRepository, SqliteDownloadRepository};

use crate::{
    load_queue_ui_state,
    open_external,
    CategoryFilter,
    SortState,
};

pub(crate) struct QueueActionResult {
    pub(crate) changed_count: usize,
    pub(crate) status_message: String,
}

pub(crate) struct OpenPathsResult {
    pub(crate) opened_count: usize,
    pub(crate) missing_count: usize,
}

pub(crate) fn effective_checked_ids(
    queue_id: i64,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    selected_download: Arc<Mutex<Option<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> Vec<String> {
    let state = load_queue_ui_state(queue_id, sort_state, category_filter);
    let visible_ids = state.row_ids.into_iter().collect::<HashSet<_>>();
    let mut ids = checked_downloads
        .lock()
        .map(|set| {
            set.iter()
                .filter(|id| visible_ids.contains(*id))
                .cloned()
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();
    if ids.is_empty() {
        if let Some(id) = selected_download.lock().ok().and_then(|v| v.clone()) {
            ids.push(id);
        }
    }
    ids
}

pub(crate) fn update_jobs_status(ids: &[String], status: &str, pause_before_update: bool) -> usize {
    if ids.is_empty() {
        return 0;
    }
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return 0; };
    let _ = repo.init_schema();
    let mut updated = 0usize;
    for id in ids {
        let current = repo.get_queue_job(id).ok().flatten();
        let should_update = match (current.as_ref().map(|job| job.status.as_str()), status) {
            (Some("Paused"), "Paused") => false,
            (Some("Queued"), "Queued") => false,
            (Some(_), _) => true,
            (None, _) => false,
        };
        if !should_update {
            continue;
        }
        if pause_before_update {
            let _ = pause_active_job(id);
        }
        if repo.update_queue_job_status(id, status).is_ok() {
            updated += 1;
        }
    }
    updated
}

pub(crate) fn pause_all_jobs(queue_id: i64) -> usize {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return 0; };
    let _ = repo.init_schema();
    let Ok(jobs) = repo.list_queue_jobs() else { return 0; };
    let mut paused = 0usize;
    for job in jobs
        .into_iter()
        .filter(|job| (queue_id == 0 || job.queue_id == queue_id) && job.status != "Paused")
    {
        let _ = pause_active_job(&job.id);
        if repo.update_queue_job_status(&job.id, "Paused").is_ok() {
            paused += 1;
        }
    }
    paused
}

pub(crate) fn delete_jobs(ids: &[String]) -> usize {
    if ids.is_empty() {
        return 0;
    }
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return 0; };
    let _ = repo.init_schema();
    let mut deleted = 0usize;
    for id in ids {
        let _ = pause_active_job(id);
        if repo.delete_download_job(id).is_ok() {
            deleted += 1;
        }
    }
    deleted
}

pub(crate) fn clear_selection_after_delete(
    ids: &[String],
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    selected_download: Arc<Mutex<Option<String>>>,
) {
    if let Ok(mut checked) = checked_downloads.lock() {
        for id in ids {
            checked.remove(id);
        }
    }
    if let Ok(mut selected) = selected_download.lock() {
        *selected = None;
    }
}

pub(crate) fn selected_resume_result(updated: usize) -> QueueActionResult {
    QueueActionResult {
        changed_count: updated,
        status_message: if updated == 0 {
            "No paused downloads selected to resume".to_string()
        } else {
            format!("Resumed {updated} download(s)")
        },
    }
}

pub(crate) fn selected_pause_result(updated: usize) -> QueueActionResult {
    QueueActionResult {
        changed_count: updated,
        status_message: if updated == 0 {
            "No active downloads selected to pause".to_string()
        } else {
            format!("Paused {updated} download(s)")
        },
    }
}

pub(crate) fn stop_all_result(paused: usize) -> QueueActionResult {
    QueueActionResult {
        changed_count: paused,
        status_message: if paused == 0 {
            "No active downloads found to stop in selected queue".to_string()
        } else {
            format!("Stopped {paused} download(s) in selected queue")
        },
    }
}

pub(crate) fn delete_result(deleted: usize) -> QueueActionResult {
    QueueActionResult {
        changed_count: deleted,
        status_message: if deleted == 0 {
            "No downloads were deleted".to_string()
        } else {
            format!("Deleted {deleted} download(s)")
        },
    }
}

pub(crate) fn open_multiple_selected_paths(ids: &[String], folder: bool) -> OpenPathsResult {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return OpenPathsResult {
            opened_count: 0,
            missing_count: 0,
        };
    };
    let _ = repo.init_schema();
    let mut opened_count = 0usize;
    let mut missing_count = 0usize;
    for id in ids {
        let Ok(Some(job)) = repo.get_queue_job(id) else { continue; };
        let target = if folder {
            std::path::PathBuf::from(job.output_dir)
        } else {
            std::path::PathBuf::from(job.output_dir).join(job.file_name)
        };
        if target.exists() {
            open_external(target.to_string_lossy().as_ref());
            opened_count += 1;
        } else {
            missing_count += 1;
        }
    }
    OpenPathsResult {
        opened_count,
        missing_count,
    }
}

pub(crate) fn selected_open_result(result: &OpenPathsResult, folder: bool) -> QueueActionResult {
    QueueActionResult {
        changed_count: result.opened_count,
        status_message: if result.opened_count == 0 && result.missing_count == 0 {
            if folder {
                "No download selected to open folder".to_string()
            } else {
                "No download selected to open".to_string()
            }
        } else if result.opened_count > 0 && result.missing_count == 0 {
            if folder {
                format!("Opening folder for {} download(s)", result.opened_count)
            } else {
                format!("Opening {} download(s)", result.opened_count)
            }
        } else if result.opened_count == 0 {
            if folder {
                "Selected download folder was not found".to_string()
            } else {
                "Selected download file was not found".to_string()
            }
        } else if folder {
            format!("Opening folder for {} download(s) ({} missing)", result.opened_count, result.missing_count)
        } else {
            format!("Opening {} download(s) ({} missing)", result.opened_count, result.missing_count)
        },
    }
}

pub(crate) fn mutate_selected_job_with_status<F>(
    selected_download: Arc<Mutex<Option<String>>>,
    success_message: &str,
    missing_message: &str,
    mutator: F,
) -> QueueActionResult
where
    F: FnOnce(&SqliteDownloadRepository, &str) -> bool,
{
    let Some(id) = selected_download.lock().ok().and_then(|v| v.clone()) else {
        return QueueActionResult {
            changed_count: 0,
            status_message: missing_message.to_string(),
        };
    };
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return QueueActionResult {
            changed_count: 0,
            status_message: "Queue database is not available".to_string(),
        };
    };
    let _ = repo.init_schema();
    let changed = mutator(&repo, &id);
    QueueActionResult {
        changed_count: usize::from(changed),
        status_message: if changed {
            success_message.to_string()
        } else {
            format!("{success_message} skipped")
        },
    }
}
