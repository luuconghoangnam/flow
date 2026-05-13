use std::sync::{Arc, Mutex};

use flow_core::{flow_db_path, DownloadRepository, SqliteDownloadRepository};

use crate::home_action_menu_state::{derive_home_action_menu_state, HomeActionMenuState};
use crate::home_action_state::HomeActionState;
use crate::home_actions::{
    available_move_category_targets, available_move_queue_targets, build_properties_summary,
    copy_selected_links, move_selected_jobs_to_category, move_selected_jobs_to_queue,
};
use crate::queue_actions::{delete_jobs, delete_result, selected_pause_result, selected_resume_result, update_jobs_status};
use crate::{load_queue_ui_state, CategoryFilter, SortState};

#[derive(Clone, Debug, Default)]
pub(crate) struct HomeActionRegistry {
    pub(crate) action_state: HomeActionState,
    pub(crate) menu_state: HomeActionMenuState,
}

#[derive(Clone, Debug)]
pub(crate) struct PropertiesDialogPayload {
    pub(crate) summary: String,
}

#[derive(Clone, Debug)]
pub(crate) struct EditDialogPayload {
    pub(crate) id: String,
    pub(crate) file_name: String,
    pub(crate) output_dir: String,
    pub(crate) priority: i32,
}

#[derive(Clone, Debug)]
pub(crate) struct FileChecksumDialogPayload {
    pub(crate) summary: String,
}

#[derive(Clone, Debug)]
pub(crate) struct QueueMoveExecution {
    pub(crate) moved_count: usize,
    pub(crate) target_queue_id: i64,
    pub(crate) target_queue_name: String,
}

#[derive(Clone, Debug)]
pub(crate) struct CategoryMoveExecution {
    pub(crate) moved_count: usize,
    pub(crate) category: String,
}

#[derive(Clone, Debug)]
pub(crate) struct BatchActionExecution {
    pub(crate) status_message: String,
}

pub(crate) fn derive_home_action_registry(
    queue_id: i64,
    action_state: HomeActionState,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> HomeActionRegistry {
    let menu_state = derive_home_action_menu_state(
        &action_state,
        available_move_queue_targets(queue_id, sort_state, category_filter),
        available_move_category_targets(),
    );
    HomeActionRegistry { action_state, menu_state }
}

pub(crate) fn execute_resume_selected(registry: &HomeActionRegistry, queue_id: i64) -> String {
    let result = selected_resume_result(update_jobs_status(&registry.action_state.resumable_ids, "Queued", false));
    if result.changed_count > 0 {
        if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
            let _ = repo.init_schema();
            let _ = repo.log_queue_event(queue_id, "queue_resumed_selected", Some(&format!("{{\"count\":{}}}", result.changed_count)));
        }
    }
    result.status_message
}

pub(crate) fn execute_pause_selected(registry: &HomeActionRegistry, queue_id: i64) -> String {
    let result = selected_pause_result(update_jobs_status(&registry.action_state.pausable_ids, "Paused", true));
    if result.changed_count > 0 {
        if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
            let _ = repo.init_schema();
            let _ = repo.log_queue_event(queue_id, "queue_paused_selected", Some(&format!("{{\"count\":{}}}", result.changed_count)));
        }
    }
    result.status_message
}

pub(crate) fn execute_delete_selected(registry: &HomeActionRegistry) -> String {
    delete_result(delete_jobs(&registry.action_state.selected_ids)).status_message
}

pub(crate) fn execute_restart_selected(registry: &HomeActionRegistry) -> BatchActionExecution {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return BatchActionExecution {
            status_message: "Queue database is not available".to_string(),
        };
    };
    let _ = repo.init_schema();
    let mut changed_count = 0usize;
    for id in &registry.action_state.selected_ids {
        if repo.reset_queue_job_for_retry(id).is_ok() {
            let _ = repo.update_queue_job_status(id, "Queued");
            changed_count += 1;
        }
    }
    BatchActionExecution {
        status_message: if changed_count == 0 {
            "No downloads restarted".to_string()
        } else {
            format!("Restarted {} download(s)", changed_count)
        },
    }
}

pub(crate) fn execute_copy_selected_links(registry: &HomeActionRegistry) -> String {
    copy_selected_links(&registry.action_state.selected_ids).status_message
}

pub(crate) fn execute_copy_as_curl(registry: &HomeActionRegistry) -> BatchActionExecution {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return BatchActionExecution {
            status_message: "Queue database is not available".to_string(),
        };
    };
    let _ = repo.init_schema();
    let commands = registry
        .action_state
        .selected_ids
        .iter()
        .filter_map(|id| repo.get_queue_job(id).ok().flatten())
        .map(|job| {
            let mut parts = vec![format!("curl -L \"{}\"", job.url.replace('"', "\\\""))];
            if let Some(referrer) = job.referrer.filter(|value| !value.trim().is_empty()) {
                parts.push(format!("-e \"{}\"", referrer.replace('"', "\\\"")));
            }
            if let Some(user_agent) = job.user_agent.filter(|value| !value.trim().is_empty()) {
                parts.push(format!("-A \"{}\"", user_agent.replace('"', "\\\"")));
            }
            if let Some(cookies) = job.cookies.filter(|value| !value.trim().is_empty()) {
                parts.push(format!("-H \"Cookie: {}\"", cookies.replace('"', "\\\"")));
            }
            if let Some(headers_json) = job.headers_json.filter(|value| !value.trim().is_empty()) {
                if let Ok(headers) = serde_json::from_str::<Vec<(String, String)>>(&headers_json) {
                    for (name, value) in headers {
                        if !name.trim().is_empty() {
                            parts.push(format!("-H \"{}: {}\"", name.replace('"', "\\\""), value.replace('"', "\\\"")));
                        }
                    }
                }
            }
            if let (Some(username), Some(password)) = (job.username.filter(|value| !value.trim().is_empty()), job.password.filter(|value| !value.trim().is_empty())) {
                parts.push(format!("-u \"{}:{}\"", username.replace('"', "\\\""), password.replace('"', "\\\"")));
            }
            parts.join(" ")
        })
        .collect::<Vec<_>>();
    if commands.is_empty() {
        return BatchActionExecution {
            status_message: "No downloads available to copy as cURL".to_string(),
        };
    }
    let payload = commands.join("\r\n");
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
    BatchActionExecution {
        status_message: if copied {
            format!("Copied {} download(s) as cURL", commands.len())
        } else {
            "Failed to copy selected downloads as cURL".to_string()
        },
    }
}

pub(crate) fn execute_show_selected_properties(
    registry: &HomeActionRegistry,
    queue_id: i64,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> Result<PropertiesDialogPayload, &'static str> {
    let Some(default_index) = registry.action_state.default_item_index else {
        return Err("No download selected to show properties");
    };
    let state = load_queue_ui_state(queue_id, sort_state, category_filter);
    let Some(id) = state.row_ids.get(default_index) else {
        return Err("Unable to resolve selected download properties");
    };
    let Some(summary) = build_properties_summary(id) else {
        return Err("Unable to load selected download properties");
    };
    Ok(PropertiesDialogPayload { summary })
}

pub(crate) fn execute_open_edit_dialog(
    registry: &HomeActionRegistry,
    queue_id: i64,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> Result<EditDialogPayload, &'static str> {
    let Some(default_index) = registry.action_state.default_item_index else {
        return Err("No download selected to edit");
    };
    let state = load_queue_ui_state(queue_id, sort_state, category_filter);
    let Some(id) = state.row_ids.get(default_index) else {
        return Err("Unable to resolve selected download");
    };
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return Err("Queue database is not available");
    };
    let _ = repo.init_schema();
    let Some(job) = repo.get_queue_job(id).ok().flatten() else {
        return Err("Unable to load selected download");
    };
    Ok(EditDialogPayload {
        id: job.id,
        file_name: job.file_name,
        output_dir: job.output_dir,
        priority: job.priority as i32,
    })
}

pub(crate) fn execute_open_file_checksum_dialog(
    registry: &HomeActionRegistry,
) -> Result<FileChecksumDialogPayload, &'static str> {
    let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) else {
        return Err("Queue database is not available");
    };
    let _ = repo.init_schema();
    let jobs = registry
        .action_state
        .selected_ids
        .iter()
        .filter_map(|id| repo.get_queue_job(id).ok().flatten())
        .filter(|job| job.status.eq_ignore_ascii_case("finished"))
        .collect::<Vec<_>>();
    if jobs.is_empty() {
        return Err("No finished downloads selected for file checksum");
    }
    let summary = jobs
        .iter()
        .map(|job| {
            format!(
                "{}\nExpected SHA-256: {}\nSaved to: {}\\{}",
                job.file_name,
                job.expected_sha256_hex.clone().unwrap_or_else(|| "Not set".to_string()),
                job.output_dir,
                job.file_name,
            )
        })
        .collect::<Vec<_>>()
        .join("\n\n");
    Ok(FileChecksumDialogPayload { summary })
}

pub(crate) fn execute_move_to_queue(
    registry: &HomeActionRegistry,
    target_index: i32,
) -> Result<QueueMoveExecution, &'static str> {
    let Some(target) = registry.menu_state.move_to_queue.resolve(target_index) else {
        return Err("Unable to resolve move-to-queue target");
    };
    let Some(result) = move_selected_jobs_to_queue(&registry.action_state.selected_ids, target.id) else {
        return Err("No downloads moved to another queue");
    };
    Ok(QueueMoveExecution {
        moved_count: result.moved_count,
        target_queue_id: result.target_queue_id,
        target_queue_name: result.target_queue_name,
    })
}

pub(crate) fn execute_move_to_category(
    registry: &HomeActionRegistry,
    target_index: i32,
) -> Result<CategoryMoveExecution, &'static str> {
    let Some(target) = registry.menu_state.move_to_category.resolve(target_index) else {
        return Err("Unable to resolve move-to-category target");
    };
    let Some(result) = move_selected_jobs_to_category(&registry.action_state.selected_ids, &target.label) else {
        return Err("No downloads moved to another category");
    };
    Ok(CategoryMoveExecution {
        moved_count: result.moved_count,
        category: result.category,
    })
}
