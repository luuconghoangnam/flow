use std::sync::{Arc, Mutex};

use flow_core::{flow_db_path, DownloadRepository, SqliteDownloadRepository};

use crate::{load_queue_ui_state, CategoryFilter, SortState};

#[derive(Clone, Debug, Default)]
pub(crate) struct MoveQueueTarget {
    pub(crate) id: i64,
    pub(crate) label: String,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct MoveCategoryTarget {
    pub(crate) label: String,
}

pub(crate) struct MoveSelectionResult {
    pub(crate) moved_count: usize,
    pub(crate) target_queue_id: i64,
    pub(crate) target_queue_name: String,
}

pub(crate) struct CopyLinksResult {
    pub(crate) status_message: String,
}

pub(crate) struct MoveCategoryResult {
    pub(crate) moved_count: usize,
    pub(crate) category: String,
}

pub(crate) fn available_move_queue_targets(
    current_queue_id: i64,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> Vec<MoveQueueTarget> {
    let state = load_queue_ui_state(current_queue_id, sort_state, category_filter);
    state
        .queue_ids
        .into_iter()
        .zip(state.queue_labels)
        .filter(|(queue_id, _)| *queue_id != current_queue_id)
        .map(|(id, label)| MoveQueueTarget { id, label: label.to_string() })
        .collect()
}

pub(crate) fn move_selected_jobs_to_queue(ids: &[String], target_queue_id: i64) -> Option<MoveSelectionResult> {
    if ids.is_empty() {
        return None;
    }
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return None; };
    let _ = repo.init_schema();

    let target_queue_name = repo
        .get_queue_group(target_queue_id)
        .ok()
        .flatten()
        .map(|group| group.name)
        .unwrap_or_else(|| format!("Queue {target_queue_id}"));

    let mut moved_count = 0usize;
    for id in ids {
        let Ok(Some(mut job)) = repo.get_queue_job(id) else { continue; };
        if job.queue_id == target_queue_id {
            continue;
        }
        let source_queue_id = job.queue_id;
        let source_status = job.status.clone();
        let queue_jobs = repo.list_queue_jobs().ok().unwrap_or_default();
        let max_order = queue_jobs
            .iter()
            .filter(|row| row.queue_id == target_queue_id)
            .map(|row| row.queue_order)
            .max()
            .unwrap_or(-1);
        job.queue_id = target_queue_id;
        job.queue_order = max_order + 1;
        if repo.upsert_queue_job(&job).is_ok() {
            let payload = format!(
                "{{\"id\":\"{}\",\"from_queue\":{},\"to_queue\":{},\"status\":\"{}\"}}",
                job.id, source_queue_id, target_queue_id, source_status
            );
            let _ = repo.log_queue_event(target_queue_id, "job_moved_queue", Some(&payload));
            moved_count += 1;
        }
    }

    if moved_count == 0 {
        None
    } else {
        Some(MoveSelectionResult {
            moved_count,
            target_queue_id,
            target_queue_name,
        })
    }
}

pub(crate) fn copy_selected_links(ids: &[String]) -> CopyLinksResult {
    if ids.is_empty() {
        return CopyLinksResult {
            status_message: "No selected downloads to copy".to_string(),
        };
    }
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return CopyLinksResult {
            status_message: "Queue database is not available".to_string(),
        };
    };
    let _ = repo.init_schema();
    let links = ids
        .iter()
        .filter_map(|id| repo.get_queue_job(id).ok().flatten().map(|job| job.url))
        .collect::<Vec<_>>();
    if links.is_empty() {
        return CopyLinksResult {
            status_message: "No selected downloads to copy".to_string(),
        };
    }
    let payload = links.join("\r\n");
    let copied = std::process::Command::new("cmd")
        .args(["/C", "clip"])
        .stdin(std::process::Stdio::piped())
        .spawn()
        .and_then(|mut child| {
            use std::io::Write;
            if let Some(stdin) = child.stdin.as_mut() {
                let _ = stdin.write_all(payload.as_bytes());
            }
            child.wait()
        })
        .map(|status| status.success())
        .unwrap_or(false);
    CopyLinksResult {
        status_message: if copied {
            format!("Copied {} download link(s)", links.len())
        } else {
            "Failed to copy selected download links".to_string()
        },
    }
}

pub(crate) fn build_properties_summary(id: &str) -> Option<String> {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return None; };
    let _ = repo.init_schema();
    let job = repo.get_queue_job(id).ok().flatten()?;
    Some(format!(
        "File: {}\nCategory: {}\nStatus: {}\nQueue ID: {}\nFolder: {}\nURL: {}\nConnections: {}\nAttempts: {}{}",
        job.file_name,
        job.category,
        job.status,
        job.queue_id,
        job.output_dir,
        job.url,
        job.connections,
        job.attempt_count,
        job.last_error
            .as_ref()
            .map(|err| format!("\nLast Error: {}", err))
            .unwrap_or_default(),
    ))
}

pub(crate) fn available_move_category_targets() -> Vec<MoveCategoryTarget> {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return Vec::new(); };
    let _ = repo.init_schema();
    let mut categories = repo
        .list_queue_jobs()
        .ok()
        .unwrap_or_default()
        .into_iter()
        .map(|job| job.category)
        .filter(|category| !category.trim().is_empty())
        .collect::<Vec<_>>();
    categories.sort();
    categories.dedup();
    if !categories.iter().any(|category| category.eq_ignore_ascii_case("General")) {
        categories.insert(0, "General".to_string());
    }
    categories
        .into_iter()
        .map(|label| MoveCategoryTarget { label })
        .collect()
}

pub(crate) fn move_selected_jobs_to_category(ids: &[String], category: &str) -> Option<MoveCategoryResult> {
    if ids.is_empty() {
        return None;
    }
    let normalized = category.trim();
    if normalized.is_empty() {
        return None;
    }
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else { return None; };
    let _ = repo.init_schema();
    let mut moved_count = 0usize;
    let mut first_queue_id = None;
    for id in ids {
        let Ok(Some(job)) = repo.get_queue_job(id) else { continue; };
        if job.category.eq_ignore_ascii_case(normalized) {
            continue;
        }
        first_queue_id.get_or_insert(job.queue_id);
        if repo.update_queue_job_category(id, normalized).is_ok() {
            moved_count += 1;
        }
    }
    if moved_count == 0 {
        return None;
    }
    if let Some(queue_id) = first_queue_id {
        let payload = format!("{{\"count\":{},\"category\":\"{}\"}}", moved_count, normalized);
        let _ = repo.log_queue_event(queue_id, "job_moved_category", Some(&payload));
    }
    Some(MoveCategoryResult {
        moved_count,
        category: normalized.to_string(),
    })
}
