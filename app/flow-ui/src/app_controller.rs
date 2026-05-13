use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use flow_core::{flow_db_path, pause_active_job, DownloadRepository, SqliteDownloadRepository};
use slint::Weak;

use crate::{
    enqueue_batch_urls,
    enqueue_queue_refresh,
    set_status,
    CategoryFilter,
    MainWindow,
    SortState,
};

#[derive(Clone)]
pub(crate) struct UiRefreshHandle {
    weak: Weak<MainWindow>,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    sort_state: Arc<Mutex<SortState>>,
    category_filter: Arc<Mutex<CategoryFilter>>,
}

impl UiRefreshHandle {
    pub(crate) fn new(
        weak: Weak<MainWindow>,
        selected_download: Arc<Mutex<Option<String>>>,
        checked_downloads: Arc<Mutex<HashSet<String>>>,
        sort_state: Arc<Mutex<SortState>>,
        category_filter: Arc<Mutex<CategoryFilter>>,
    ) -> Self {
        Self {
            weak,
            selected_download,
            checked_downloads,
            sort_state,
            category_filter,
        }
    }

    pub(crate) fn refresh(&self, queue_id: i64) {
        enqueue_queue_refresh(
            &self.weak,
            queue_id,
            self.selected_download.clone(),
            self.checked_downloads.clone(),
            &self.sort_state,
            &self.category_filter,
        );
    }

    pub(crate) fn status(&self, message: &str) {
        set_status(&self.weak, message);
    }
}

pub(crate) enum AppCommand {
    StartAll,
    PauseAll,
    DeleteJobs {
        statuses: Vec<&'static str>,
        delete_all: bool,
        empty_message: &'static str,
        changed_message: &'static str,
    },
    BatchUrls {
        urls_text: String,
        output_dir: String,
        target_queue: i64,
        start_now: bool,
        category: String,
        empty_prefix: &'static str,
        done_prefix: &'static str,
    },
}

pub(crate) fn execute_app_command(handle: UiRefreshHandle, queue_id: i64, command: AppCommand) {
    std::thread::spawn(move || match command {
        AppCommand::StartAll => {
            let resumed = start_all_paused_jobs(queue_id);
            handle.refresh(queue_id);
            if resumed == 0 {
                handle.status("No paused tasks found in current queue");
            } else {
                handle.status(&format!("Started {resumed} paused task(s)"));
            }
        }
        AppCommand::PauseAll => {
            let paused = pause_all_jobs_in_queue(queue_id);
            handle.refresh(queue_id);
            if paused == 0 {
                handle.status("No active tasks found in current queue");
            } else {
                handle.status(&format!("Paused {paused} task(s) in current queue"));
            }
        }
        AppCommand::DeleteJobs {
            statuses,
            delete_all,
            empty_message,
            changed_message,
        } => {
            let removed = delete_jobs_for_current_queue(queue_id, &statuses, delete_all);
            handle.refresh(queue_id);
            if removed == 0 {
                handle.status(empty_message);
            } else {
                handle.status(&format!("{changed_message} {removed} item(s) from current queue"));
            }
        }
        AppCommand::BatchUrls {
            urls_text,
            output_dir,
            target_queue,
            start_now,
            category,
            empty_prefix,
            done_prefix,
        } => {
            let (queued, invalid) = enqueue_batch_urls(
                urls_text.as_str(),
                output_dir.as_str(),
                target_queue,
                start_now,
                category.as_str(),
            );
            handle.refresh(target_queue);
            if queued == 0 {
                handle.status(&format!("{empty_prefix}. Invalid/skipped: {invalid}"));
            } else {
                handle.status(&format!(
                    "{done_prefix}: {queued} item(s) in {category}; invalid/skipped: {invalid}"
                ));
            }
        }
    });
}

fn start_all_paused_jobs(queue_id: i64) -> usize {
    let mut resumed = 0usize;
    if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
        let _ = repo.init_schema();
        if let Ok(jobs) = repo.list_queue_jobs() {
            for job in jobs
                .into_iter()
                .filter(|job| job.queue_id == queue_id && job.status == "Paused")
            {
                if repo.update_queue_job_status(&job.id, "Queued").is_ok() {
                    resumed += 1;
                }
            }
        }
        let _ = repo.set_queue_group_active(queue_id, true);
    }
    resumed
}

fn pause_all_jobs_in_queue(queue_id: i64) -> usize {
    let mut paused = 0usize;
    if let Ok(repo) = SqliteDownloadRepository::open(&flow_db_path()) {
        let _ = repo.init_schema();
        if let Ok(jobs) = repo.list_queue_jobs() {
            for job in jobs
                .into_iter()
                .filter(|job| job.queue_id == queue_id && job.status != "Paused")
            {
                let _ = pause_active_job(&job.id);
                if repo.update_queue_job_status(&job.id, "Paused").is_ok() {
                    paused += 1;
                }
            }
        }
        let _ = repo.set_queue_group_active(queue_id, false);
    }
    paused
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
        let _ = pause_active_job(&job.id);
        if repo.delete_download_job(&job.id).is_ok() {
            removed += 1;
        }
    }
    removed
}
