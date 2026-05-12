use std::collections::HashMap;
use std::path::PathBuf;
use std::time::{Duration, Instant};

use tokio::sync::mpsc;

use crate::download::DownloadEvent;
use crate::paths::flow_signal_path;
use crate::storage::{ChunkProgress, DownloadRepository, SqliteDownloadRepository};

pub async fn persist_chunk_progress_worker(
    db_path: PathBuf,
    mut rx: mpsc::Receiver<DownloadEvent>,
    debounce_ms: u64,
) -> Result<(), String> {
    let repo = SqliteDownloadRepository::open(&db_path).map_err(|e| e.to_string())?;
    repo.init_schema().map_err(|e| e.to_string())?;

    let debounce = Duration::from_millis(debounce_ms.max(100));
    let mut cache: HashMap<(String, usize), ChunkProgress> = HashMap::new();
    let mut last_flush = Instant::now();

    while let Some(event) = rx.recv().await {
        let mut should_signal = false;
        match event {
            DownloadEvent::Queued(task) => {
                repo.update_queue_job_status(&task.id.0, "Queued").map_err(|e| e.to_string())?;
                should_signal = true;
            }
            DownloadEvent::Progress { id, .. } => {
                repo.update_queue_job_status(&id.0, "Downloading").map_err(|e| e.to_string())?;
                should_signal = true;
            }
            DownloadEvent::ChunkProgress {
                id,
                chunk_index,
                start,
                end_inclusive,
                downloaded,
            } => {
                cache.insert(
                    (id.0.clone(), chunk_index),
                    ChunkProgress {
                        download_id: id.0,
                        chunk_index: chunk_index as i64,
                        start,
                        end_inclusive,
                        downloaded,
                    },
                );
            }
            DownloadEvent::Completed(task) => {
                repo.update_queue_job_status(&task.id.0, "Completed").map_err(|e| e.to_string())?;
                should_signal = true;
            }
            DownloadEvent::Cancelled { id } => {
                repo.update_queue_job_status(&id.0, "Cancelled").map_err(|e| e.to_string())?;
                should_signal = true;
            }
            DownloadEvent::Failed { id, .. } => {
                repo.update_queue_job_status(&id.0, "Failed").map_err(|e| e.to_string())?;
                should_signal = true;
            }
        }

        if should_signal {
            touch_signal_file();
        }

        if last_flush.elapsed() >= debounce {
            flush_chunk_cache(&repo, &mut cache)?;
            touch_signal_file();
            last_flush = Instant::now();
        }
    }

    flush_chunk_cache(&repo, &mut cache)?;
    touch_signal_file();
    Ok(())
}

fn flush_chunk_cache(
    repo: &SqliteDownloadRepository,
    cache: &mut HashMap<(String, usize), ChunkProgress>,
) -> Result<(), String> {
    for chunk in cache.values() {
        repo.upsert_chunk_progress(chunk).map_err(|e| e.to_string())?;
    }
    cache.clear();
    Ok(())
}

fn touch_signal_file() {
    let path = flow_signal_path();
    let _ = std::fs::write(path, format!("{}", std::time::SystemTime::now().elapsed().map(|v| v.as_nanos()).unwrap_or_default()));
}
