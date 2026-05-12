use std::collections::VecDeque;
use std::path::PathBuf;
use std::sync::Mutex;

use once_cell::sync::Lazy;
use tokio::sync::{mpsc, Semaphore};

use crate::download::{DownloadControl, DownloadEngine, DownloadEvent, DownloadRequest, SpeedLimiter};
use crate::storage::{DownloadRepository, QueueGroupRecord, QueueJobRecord, SqliteDownloadRepository};

#[derive(serde::Serialize, serde::Deserialize, Debug, Clone)]
pub struct ScheduleConfig {
    pub enabled: bool,
    pub start_time_minutes: u32,
    pub stop_time_minutes: u32,
    pub days_of_week: Vec<u8>,
}

pub fn is_schedule_allowed(config_json: Option<&str>) -> bool {
    let Some(json) = config_json else { return true; };
    let Ok(config) = serde_json::from_str::<ScheduleConfig>(json) else { return true; };
    if !config.enabled { return true; }
    
    use chrono::{Local, Timelike, Datelike};
    let now = Local::now();
    let current_minutes = now.hour() * 60 + now.minute();
    let current_day = now.weekday().num_days_from_sunday() as u8;
    
    if !config.days_of_week.is_empty() && !config.days_of_week.contains(&current_day) {
        return false;
    }
    
    if config.start_time_minutes < config.stop_time_minutes {
        current_minutes >= config.start_time_minutes && current_minutes <= config.stop_time_minutes
    } else {
        current_minutes >= config.start_time_minutes || current_minutes <= config.stop_time_minutes
    }
}

static ACTIVE_CONTROLS: Lazy<Mutex<std::collections::HashMap<String, DownloadControl>>> =
    Lazy::new(|| Mutex::new(std::collections::HashMap::new()));

pub struct QueueScheduler {
    max_concurrent: usize,
    pending: VecDeque<QueueJobRecord>,
}

impl QueueScheduler {
    pub fn new(max_concurrent: usize) -> Self {
        Self {
            max_concurrent: max_concurrent.max(1),
            pending: VecDeque::new(),
        }
    }

    pub fn enqueue(&mut self, job: QueueJobRecord) {
        let pos = self
            .pending
            .iter()
            .position(|existing| job.priority > existing.priority)
            .unwrap_or(self.pending.len());
        self.pending.insert(pos, job);
    }

    pub fn enqueue_many(&mut self, jobs: Vec<QueueJobRecord>) {
        for job in jobs {
            self.enqueue(job);
        }
    }

    pub async fn run(mut self, tx: mpsc::Sender<DownloadEvent>) {
        let semaphore = std::sync::Arc::new(Semaphore::new(self.max_concurrent));
        let global_limiter: Option<SpeedLimiter> = None;
        while let Some(job) = self.pending.pop_front() {
            let permit = match semaphore.clone().acquire_owned().await {
                Ok(permit) => permit,
                Err(_) => break,
            };
            let tx2 = tx.clone();
            let global_limiter2 = global_limiter.clone();
            tokio::spawn(async move {
                let _permit = permit;
                let engine = DownloadEngine::new().with_global_limiter(global_limiter2);
                let request = DownloadRequest::from(job);
                let control = DownloadControl::default();
                let _ = engine.download_multi_connection(request, tx2, control).await;
            });
        }
    }

    pub async fn run_channel(
        db_path: PathBuf,
        mut jobs: mpsc::Receiver<QueueJobRecord>,
        events: mpsc::Sender<DownloadEvent>,
    ) {
        let mut semaphores: std::collections::HashMap<i64, std::sync::Arc<Semaphore>> = std::collections::HashMap::new();
        
        while let Some(job) = jobs.recv().await {
            let mut batch = vec![job];
            while let Ok(next) = jobs.try_recv() {
                batch.push(next);
            }
            batch.sort_by(|a, b| {
                b.priority
                    .cmp(&a.priority)
                    .then_with(|| a.queue_id.cmp(&b.queue_id))
                    .then_with(|| a.queue_order.cmp(&b.queue_order))
            });

            for job in batch {
                let repo = match SqliteDownloadRepository::open(&db_path) {
                    Ok(r) => r,
                    Err(_) => continue,
                };
                let group = match repo.get_queue_group(job.queue_id) {
                    Ok(Some(g)) => g,
                    _ => continue,
                };

                if !group.active {
                    let _ = repo.log_queue_event(job.queue_id, "schedule_blocked_inactive", Some(&format!("{{\"id\":\"{}\"}}", job.id)));
                    continue;
                }

                if !is_schedule_allowed(group.schedule_json.as_deref()) {
                    let _ = repo.log_queue_event(job.queue_id, "schedule_blocked_time", Some(&format!("{{\"id\":\"{}\"}}", job.id)));
                    continue;
                }

                let permits = group.max_concurrent.max(1) as usize;
                let semaphore = semaphores.entry(job.queue_id).or_insert_with(|| std::sync::Arc::new(Semaphore::new(permits))).clone();

                let permit = match semaphore.acquire_owned().await {
                    Ok(permit) => permit,
                    Err(_) => continue,
                };
                
                let settings = crate::settings::load_settings(&crate::paths::flow_data_dir().join("settings.json"));
                let global_limiter = settings.global_speed_limit_bps.filter(|v| *v > 0).map(SpeedLimiter::new);

                let events2 = events.clone();
                let db_path2 = db_path.clone();
                tokio::spawn(async move {
                    let _permit = permit;
                    run_job_with_retry(job, db_path2, events2, 3, global_limiter).await;
                });
            }
        }
    }
}

pub fn pause_active_job(download_id: &str) -> bool {
    if let Some(control) = ACTIVE_CONTROLS.lock().ok().and_then(|map| map.get(download_id).cloned()) {
        control.pause();
        true
    } else {
        false
    }
}

pub fn resume_active_job(download_id: &str) -> bool {
    if let Some(control) = ACTIVE_CONTROLS.lock().ok().and_then(|map| map.get(download_id).cloned()) {
        control.resume();
        true
    } else {
        false
    }
}

async fn run_job_with_retry(
    job: QueueJobRecord,
    db_path: PathBuf,
    events: mpsc::Sender<DownloadEvent>,
    attempts: usize,
    global_limiter: Option<SpeedLimiter>,
) {
    let mut attempt = 0;
    loop {
        attempt += 1;
        if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
            if let Ok(Some(current)) = repo.get_queue_job(&job.id) {
                if current.status == "Paused" || current.status == "Cancelled" {
                    break;
                }
            }
        }
        let engine = DownloadEngine::new().with_global_limiter(global_limiter.clone());
        let request = DownloadRequest::from(job.clone());
        let control = DownloadControl::default();
        if let Ok(mut map) = ACTIVE_CONTROLS.lock() {
            map.insert(job.id.clone(), control.clone());
        }
        let persisted = match SqliteDownloadRepository::open(&db_path) {
            Ok(repo) => {
                let _ = repo.init_schema();
                let _ = repo.log_queue_event(job.queue_id, "job_starting", Some(&format!("{{\"id\":\"{}\"}}", job.id)));
                let _ = repo.update_queue_job_status(&job.id, "Downloading");
                let _ = repo.update_queue_job_attempt(&job.id, attempt as i64, None);
                repo.list_chunk_progress(&job.id).unwrap_or_default()
            }
            Err(_) => Vec::new(),
        };
        let result = engine
            .download_multi_connection_with_resume(request, events.clone(), control, persisted)
            .await;
        if let Ok(mut map) = ACTIVE_CONTROLS.lock() {
            map.remove(&job.id);
        }

        if result.is_ok() || attempt >= attempts {
            if result.is_err() {
                let reason = result.err().unwrap_or_else(|| "Unknown queue error".to_string());
                if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
                    let _ = repo.update_queue_job_attempt(&job.id, attempt as i64, Some(&reason));
                    let _ = repo.update_queue_job_status(&job.id, "Failed");
                    let _ = repo.log_queue_event(job.queue_id, "job_failed", Some(&format!("{{\"id\":\"{}\",\"reason\":\"{}\"}}", job.id, reason.replace('"', "'"))));
                }
            }
            evaluate_stop_on_empty(&db_path, job.queue_id, Some(&events));
            break;
        }

        if let Err(reason) = &result {
            if let Ok(repo) = SqliteDownloadRepository::open(&db_path) {
                let _ = repo.update_queue_job_attempt(&job.id, attempt as i64, Some(reason));
            }
        }

        tokio::time::sleep(std::time::Duration::from_secs(attempt as u64)).await;
    }
}

fn evaluate_stop_on_empty(db_path: &PathBuf, queue_id: i64, events: Option<&mpsc::Sender<DownloadEvent>>) {
    let Ok(repo) = SqliteDownloadRepository::open(db_path) else { return; };
    let Ok(Some(group)) = repo.get_queue_group(queue_id) else { return; };
    if !group.stop_on_empty {
        return;
    }
    let Ok(jobs) = repo.list_queue_jobs() else { return; };
    let has_runnable = jobs.iter().any(|job| {
        job.queue_id == queue_id && matches!(job.status.as_str(), "Queued" | "Downloading")
    });
    if !has_runnable {
        let _ = repo.set_queue_group_active(queue_id, false);
        let _ = repo.log_queue_event(queue_id, "queue_became_empty", None);
        if let Some(events) = events {
            let _ = events.try_send(DownloadEvent::QueueEmpty { queue_id });
        }
    }
}

impl From<QueueJobRecord> for DownloadRequest {
    fn from(job: QueueJobRecord) -> Self {
        let output_dir = PathBuf::from(job.output_dir);
        Self {
            url: job.url,
            output_dir: normalize_output_dir(output_dir),
            file_name: job.file_name,
            connections: job.connections.max(1),
            expected_sha256_hex: job.expected_sha256_hex,
            headers: job
                .headers_json
                .and_then(|value| serde_json::from_str(&value).ok())
                .unwrap_or_default(),
            referrer: job.referrer,
            cookies: job.cookies,
            user_agent: job.user_agent,
            username: job.username,
            password: job.password,
            proxy_url: job.proxy_url,
            proxy_username: job.proxy_username,
            proxy_password: job.proxy_password,
            priority: job.priority,
            queue_id: job.queue_id,
            speed_limit_bps: None,
        }
    }
}

fn normalize_output_dir(output_dir: PathBuf) -> PathBuf {
    if output_dir.is_absolute() {
        return output_dir;
    }
    std::env::var_os("USERPROFILE")
        .map(PathBuf::from)
        .map(|home| home.join("Downloads"))
        .unwrap_or(output_dir)
}
