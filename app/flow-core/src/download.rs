use std::path::{Path, PathBuf};
use std::collections::BTreeMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::{Duration, Instant};

use futures_util::StreamExt;
use reqwest::header::{HeaderMap, HeaderName, HeaderValue, ACCEPT_ENCODING, ACCEPT_RANGES, CONTENT_DISPOSITION, CONTENT_LENGTH, CONTENT_RANGE, RANGE, REFERER, USER_AGENT};
use sha2::{Digest, Sha256};
use tokio::fs::{self, OpenOptions};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::sync::mpsc;

use crate::model::{DownloadId, DownloadStatus, DownloadTask};
use crate::storage::{ChunkProgress, DownloadRepository};

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
struct ResumeValidationMetadata {
    etag: Option<String>,
    last_modified: Option<String>,
}

#[derive(Debug, Clone)]
pub struct DownloadRequest {
    pub url: String,
    pub output_dir: PathBuf,
    pub file_name: String,
    pub connections: usize,
    pub expected_sha256_hex: Option<String>,
    pub headers: BTreeMap<String, String>,
    pub referrer: Option<String>,
    pub cookies: Option<String>,
    pub user_agent: Option<String>,
    pub username: Option<String>,
    pub password: Option<String>,
    pub proxy_url: Option<String>,
    pub proxy_username: Option<String>,
    pub proxy_password: Option<String>,
    pub priority: i64,
    pub queue_id: i64,
    pub speed_limit_bps: Option<u64>,
}

#[derive(Debug, Clone)]
pub struct ChunkPlan {
    pub start: u64,
    pub end_inclusive: u64,
}

#[derive(Debug, Clone)]
pub enum DownloadEvent {
    Queued(DownloadTask),
    Progress { id: DownloadId, downloaded_bytes: u64, total_bytes: Option<u64> },
    ChunkProgress {
        id: DownloadId,
        chunk_index: usize,
        start: u64,
        end_inclusive: u64,
        downloaded: u64,
    },
    Completed(DownloadTask),
    Cancelled { id: DownloadId },
    Failed { id: DownloadId, reason: String },
}

#[derive(Clone, Default)]
pub struct DownloadControl {
    paused: Arc<AtomicBool>,
    cancelled: Arc<AtomicBool>,
}

impl DownloadControl {
    pub fn pause(&self) { self.paused.store(true, Ordering::SeqCst); }
    pub fn resume(&self) { self.paused.store(false, Ordering::SeqCst); }
    pub fn cancel(&self) { self.cancelled.store(true, Ordering::SeqCst); }
    fn is_paused(&self) -> bool { self.paused.load(Ordering::SeqCst) }
    fn is_cancelled(&self) -> bool { self.cancelled.load(Ordering::SeqCst) }
}

pub struct DownloadEngine {
    client: reqwest::Client,
}

#[derive(Clone)]
struct SpeedLimiter {
    inner: Arc<tokio::sync::Mutex<SpeedLimiterState>>,
}

struct SpeedLimiterState {
    bps: u64,
    tokens: f64,
    last_refill: Instant,
}

impl SpeedLimiter {
    fn new(bps: u64) -> Self {
        Self {
            inner: Arc::new(tokio::sync::Mutex::new(SpeedLimiterState {
                bps,
                tokens: bps as f64,
                last_refill: Instant::now(),
            })),
        }
    }

    async fn consume(&self, bytes: usize) {
        let need = bytes as f64;
        loop {
            let mut state = self.inner.lock().await;
            let now = Instant::now();
            let elapsed = now.duration_since(state.last_refill).as_secs_f64();
            state.tokens = (state.tokens + elapsed * state.bps as f64).min(state.bps as f64);
            state.last_refill = now;

            if state.tokens >= need {
                state.tokens -= need;
                return;
            }

            let deficit = need - state.tokens;
            let wait_seconds = deficit / state.bps as f64;
            drop(state);
            tokio::time::sleep(Duration::from_secs_f64(wait_seconds.max(0.001))).await;
        }
    }
}

impl DownloadEngine {
    pub fn new() -> Self {
        Self { client: reqwest::Client::new() }
    }

    pub async fn enqueue(&self, request: DownloadRequest) -> DownloadTask {
        let id = DownloadId(format!("dl-{}", uuid_like_seed(&request.url)));
        DownloadTask {
            id,
            url: request.url,
            output_path: request.output_dir,
            file_name: request.file_name,
            total_bytes: None,
            downloaded_bytes: 0,
            status: DownloadStatus::Queued,
        }
    }

    pub async fn probe(&self, url: &str) -> Result<(Option<u64>, bool), reqwest::Error> {
        let response = self.apply_request_headers(self.client.head(url), None).send().await?;
        let total_bytes = response
            .headers()
            .get(CONTENT_LENGTH)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.parse::<u64>().ok());
        let accept_ranges = response
            .headers()
            .get(ACCEPT_RANGES)
            .and_then(|v| v.to_str().ok())
            .map(|v| v.eq_ignore_ascii_case("bytes"))
            .unwrap_or(false);
        Ok((total_bytes, accept_ranges))
    }

    pub fn make_chunk_plan(total_bytes: u64, connections: usize) -> Vec<ChunkPlan> {
        const MIN_PART_SIZE: u64 = 1024 * 1024;
        let max_part_count = connections.max(1) as u64;
        let min_parts = total_bytes.div_ceil(MIN_PART_SIZE).max(1);
        let actual_part_count = max_part_count.min(min_parts).max(1);
        let base_size = total_bytes / actual_part_count;
        let remainder = total_bytes % actual_part_count;
        let mut start = 0_u64;
        let mut plan = Vec::new();

        for index in 0..actual_part_count {
            let extra = u64::from(index < remainder);
            let end = start + base_size + extra - 1;
            plan.push(ChunkPlan { start, end_inclusive: end });
            start = end + 1;
        }
        plan
    }

    pub fn create_event_channel(buffer: usize) -> (mpsc::Sender<DownloadEvent>, mpsc::Receiver<DownloadEvent>) {
        mpsc::channel(buffer)
    }

    fn apply_request_headers(
        &self,
        builder: reqwest::RequestBuilder,
        request: Option<&DownloadRequest>,
    ) -> reqwest::RequestBuilder {
        let builder = apply_header_map(builder, &request_header_map(request));
        if let Some(request) = request {
            apply_credentials(builder, request.username.as_deref(), request.password.as_deref())
        } else {
            builder
        }
    }

    pub async fn download_single_stream(
        &self,
        request: DownloadRequest,
        tx: mpsc::Sender<DownloadEvent>,
        control: DownloadControl,
    ) -> Result<DownloadTask, String> {
        let mut task = self.enqueue(request.clone()).await;
        let _ = tx.send(DownloadEvent::Queued(task.clone())).await;

        if let Err(error) = fs::create_dir_all(&request.output_dir).await {
            let reason = format!("Failed to create output dir: {error}");
            let _ = tx.send(DownloadEvent::Failed { id: task.id.clone(), reason: reason.clone() }).await;
            return Err(reason);
        }

        task.status = DownloadStatus::Downloading;
        let mut output_file = unique_output_path(&request.output_dir.join(&request.file_name));
        let metadata_path = resume_metadata_path(&output_file);

        let mut already_downloaded = existing_size(&output_file).await.unwrap_or(0);
        let previous_validation = if already_downloaded > 0 {
            read_resume_validation_metadata(&metadata_path)
        } else {
            None
        };
        let (total_bytes, _) = self.probe(&request.url).await.map_err(|e| e.to_string())?;
        task.total_bytes = total_bytes;
        task.downloaded_bytes = already_downloaded;

        let client = client_for_request(&request).unwrap_or_else(|| self.client.clone());
        let mut builder = self.apply_request_headers(client.get(&request.url), Some(&request));
        if already_downloaded > 0 {
            builder = builder.header(RANGE, format!("bytes={already_downloaded}-"));
        }

        let response = builder.send().await.map_err(|e| e.to_string())?;
        if already_downloaded > 0 && response.status() != reqwest::StatusCode::PARTIAL_CONTENT {
            return Err(format!("Server did not resume single-stream request: got {}", response.status()));
        }
        if already_downloaded == 0 && !response.status().is_success() {
            return Err(format!("HTTP request failed with status {}", response.status()));
        }
        if task.file_name == "download.bin" {
            if let Some(filename) = extract_filename_from_response(response.headers()) {
                task.file_name = filename;
                if already_downloaded == 0 {
                    output_file = unique_output_path(&request.output_dir.join(&task.file_name));
                }
            }
        }

        if already_downloaded > 0 {
            validate_resume_headers(response.headers(), previous_validation.as_ref())?;
        } else {
            let _ = write_resume_validation_metadata(
                &metadata_path,
                &ResumeValidationMetadata {
                    etag: response
                        .headers()
                        .get("etag")
                        .and_then(|v| v.to_str().ok())
                        .map(|v| v.to_string()),
                    last_modified: response
                        .headers()
                        .get("last-modified")
                        .and_then(|v| v.to_str().ok())
                        .map(|v| v.to_string()),
                },
            );
        }
        let mut stream = response.bytes_stream();
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&output_file)
            .await
            .map_err(|e| e.to_string())?;
        let limiter = request.speed_limit_bps.filter(|v| *v > 0).map(SpeedLimiter::new);

        while let Some(chunk) = stream.next().await {
            if let Err(reason) = wait_if_paused_or_cancelled(&control).await {
                emit_control_failure(&tx, &task.id, &reason).await;
                return Err(reason);
            }
            let bytes = chunk.map_err(|e| e.to_string())?;
            if let Some(limiter) = &limiter {
                limiter.consume(bytes.len()).await;
            }
            file.write_all(&bytes).await.map_err(|e| e.to_string())?;
            already_downloaded += bytes.len() as u64;
            task.downloaded_bytes = already_downloaded;
            let _ = tx
                .send(DownloadEvent::Progress {
                    id: task.id.clone(),
                    downloaded_bytes: already_downloaded,
                    total_bytes: task.total_bytes,
                })
                .await;
        }

        task.status = DownloadStatus::Completed;
        let _ = tx.send(DownloadEvent::Completed(task.clone())).await;
        Ok(task)
    }

    pub async fn download_multi_connection(
        &self,
        request: DownloadRequest,
        tx: mpsc::Sender<DownloadEvent>,
        control: DownloadControl,
    ) -> Result<DownloadTask, String> {
        self.download_multi_connection_with_resume(request, tx, control, Vec::new())
            .await
    }

    pub async fn download_multi_connection_with_resume(
        &self,
        request: DownloadRequest,
        tx: mpsc::Sender<DownloadEvent>,
        control: DownloadControl,
        persisted_chunks: Vec<ChunkProgress>,
    ) -> Result<DownloadTask, String> {
        let mut task = self.enqueue(request.clone()).await;
        let _ = tx.send(DownloadEvent::Queued(task.clone())).await;

        fs::create_dir_all(&request.output_dir).await.map_err(|e| e.to_string())?;
        let (total_opt, accept_ranges) = match self.probe(&request.url).await {
            Ok(value) => value,
            Err(error) => {
                let reason = error.to_string();
                let _ = tx.send(DownloadEvent::Failed { id: task.id.clone(), reason: reason.clone() }).await;
                return Err(reason);
            }
        };
        let Some(total) = total_opt else {
            return self.download_single_stream(request, tx, control).await;
        };
        if !accept_ranges {
            return self.download_single_stream(request, tx, control).await;
        }

        task.total_bytes = Some(total);
        task.status = DownloadStatus::Downloading;
        let plans = Self::make_chunk_plan(total, request.connections);
        let client = Arc::new(client_for_request(&request).unwrap_or_else(|| self.client.clone()));
        let mut handles = Vec::new();
        let temp_dir = request.output_dir.join(format!(".{}.parts", task.id.0));
        fs::create_dir_all(&temp_dir).await.map_err(|e| e.to_string())?;

        let persisted_chunks = prepare_resume_chunks(&persisted_chunks, &plans, &temp_dir).await;
        let persisted_total: u64 = persisted_chunks.iter().map(|c| c.downloaded).sum();
        let aggregate = Arc::new(AtomicU64::new(persisted_total));
        let task_id = task.id.clone();
        let request_headers = request_header_map(Some(&request));
        let limiter = request.speed_limit_bps.filter(|v| *v > 0).map(SpeedLimiter::new);
        for (idx, plan) in plans.iter().enumerate() {
            let persisted = persisted_chunks.iter().find(|chunk| chunk.chunk_index == idx as i64);
            let persisted_downloaded = persisted.map(|v| v.downloaded).unwrap_or(0);
            let chunk_size = plan.end_inclusive - plan.start + 1;
            if persisted_downloaded >= chunk_size {
                let _ = tx
                    .send(DownloadEvent::ChunkProgress {
                        id: task.id.clone(),
                        chunk_index: idx,
                        start: plan.start,
                        end_inclusive: plan.end_inclusive,
                        downloaded: chunk_size,
                    })
                    .await;
                continue;
            }

            let url = request.url.clone();
            let part_path = temp_dir.join(format!("part-{idx}.bin"));
            let resume_start = plan.start + persisted_downloaded;
            let range_value = format!("bytes={resume_start}-{}", plan.end_inclusive);
            let client = Arc::clone(&client);
            let tx2 = tx.clone();
            let control2 = control.clone();
            let aggregate2 = Arc::clone(&aggregate);
            let task_id2 = task_id.clone();
            let chunk_start = plan.start;
            let chunk_end = plan.end_inclusive;
            let request_headers = request_headers.clone();
            let username = request.username.clone();
            let password = request.password.clone();
            let limiter = limiter.clone();
            handles.push(tokio::spawn(async move {
                let response = send_with_retry(|| apply_credentials(apply_header_map(client.get(&url).header(RANGE, range_value.clone()), &request_headers), username.as_deref(), password.as_deref()), 3).await?;
                validate_range_response(&response, resume_start, chunk_end)?;
                let mut stream = response.bytes_stream();
                let mut file = OpenOptions::new().create(true).append(true).open(&part_path).await.map_err(|e| e.to_string())?;
                let mut written = persisted_downloaded;
                while let Some(chunk) = stream.next().await {
                    wait_if_paused_or_cancelled(&control2).await?;
                    let bytes = chunk.map_err(|e| e.to_string())?;
                    if let Some(limiter) = &limiter {
                        limiter.consume(bytes.len()).await;
                    }
                    file.write_all(&bytes).await.map_err(|e| e.to_string())?;
                    written += bytes.len() as u64;
                    let _ = tx2
                        .send(DownloadEvent::ChunkProgress {
                            id: task_id2.clone(),
                            chunk_index: idx,
                            start: chunk_start,
                            end_inclusive: chunk_end,
                            downloaded: written,
                        })
                        .await;
                    let total_done = aggregate2.fetch_add(bytes.len() as u64, Ordering::SeqCst) + bytes.len() as u64;
                    let _ = tx2
                        .send(DownloadEvent::Progress {
                            id: task_id2.clone(),
                            downloaded_bytes: total_done,
                            total_bytes: None,
                        })
                        .await;
                }
                Ok::<u64, String>(written)
            }));
        }

        for handle in handles {
            match handle.await.map_err(|e| e.to_string())? {
                Ok(_) => {}
                Err(reason) => {
                    emit_control_failure(&tx, &task.id, &reason).await;
                    return Err(reason);
                }
            };
            task.downloaded_bytes = aggregate.load(Ordering::SeqCst);
            let _ = tx
                .send(DownloadEvent::Progress {
                    id: task.id.clone(),
                    downloaded_bytes: task.downloaded_bytes,
                    total_bytes: task.total_bytes,
                })
                .await;
        }

        let output_file = unique_output_path(&request.output_dir.join(&request.file_name));
        verify_parts_complete(&temp_dir, &plans).await?;
        merge_parts(&temp_dir, plans.len(), &output_file).await?;
        verify_output_integrity(&output_file, total, request.expected_sha256_hex.as_deref()).await?;
        let _ = fs::remove_dir_all(&temp_dir).await;

        task.status = DownloadStatus::Completed;
        task.downloaded_bytes = total;
        let _ = tx.send(DownloadEvent::Completed(task.clone())).await;
        Ok(task)
    }
}

async fn verify_output_integrity(
    output_file: &Path,
    expected_size: u64,
    expected_sha256_hex: Option<&str>,
) -> Result<(), String> {
    let size = existing_size(output_file).await.map_err(|e| e.to_string())?;
    if size != expected_size {
        return Err(format!("Output size mismatch: expected {expected_size}, got {size}"));
    }
    if let Some(expected_hash) = expected_sha256_hex {
        let bytes = fs::read(output_file).await.map_err(|e| e.to_string())?;
        let mut hasher = Sha256::new();
        hasher.update(&bytes);
        let digest = hasher.finalize();
        let actual = format!("{:x}", digest);
        if actual != expected_hash.to_lowercase() {
            return Err("SHA256 checksum mismatch".to_string());
        }
    }
    Ok(())
}

fn unique_output_path(path: &Path) -> PathBuf {
    if !path.exists() {
        return path.to_path_buf();
    }

    let parent = path.parent().unwrap_or_else(|| Path::new("."));
    let stem = path.file_stem().and_then(|v| v.to_str()).unwrap_or("download");
    let extension = path.extension().and_then(|v| v.to_str());

    for counter in 1.. {
        let file_name = match extension {
            Some(ext) if !ext.is_empty() => format!("{stem}_{counter}.{ext}"),
            _ => format!("{stem}_{counter}"),
        };
        let candidate = parent.join(file_name);
        if !candidate.exists() {
            return candidate;
        }
    }

    unreachable!()
}

fn resume_metadata_path(output_file: &Path) -> PathBuf {
    let file_name = output_file
        .file_name()
        .and_then(|v| v.to_str())
        .unwrap_or("download.bin")
        .to_string();
    output_file
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .join(format!(".{file_name}.resume.json"))
}

fn read_resume_validation_metadata(path: &Path) -> Option<ResumeValidationMetadata> {
    let text = std::fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}

fn write_resume_validation_metadata(path: &Path, metadata: &ResumeValidationMetadata) -> Result<(), String> {
    let text = serde_json::to_string(metadata).map_err(|e| e.to_string())?;
    std::fs::write(path, text).map_err(|e| e.to_string())
}

fn validate_resume_headers(
    response_headers: &HeaderMap,
    previous: Option<&ResumeValidationMetadata>,
) -> Result<(), String> {
    let Some(previous) = previous else {
        return Ok(());
    };

    let current_etag = response_headers
        .get("etag")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.to_string());
    let current_last_modified = response_headers
        .get("last-modified")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.to_string());

    if previous.etag.is_some() && current_etag.is_some() && previous.etag != current_etag {
        return Err("File changed on server (ETag mismatch)".to_string());
    }
    if previous.last_modified.is_some()
        && current_last_modified.is_some()
        && previous.last_modified != current_last_modified
    {
        return Err("File changed on server (Last-Modified mismatch)".to_string());
    }
    Ok(())
}

pub async fn run_multi_connection_with_repository(
    engine: &DownloadEngine,
    repository: &impl DownloadRepository,
    request: DownloadRequest,
    tx: mpsc::Sender<DownloadEvent>,
    control: DownloadControl,
) -> Result<DownloadTask, String> {
    let preview_task = engine.enqueue(request.clone()).await;
    let persisted = repository
        .list_chunk_progress(&preview_task.id.0)
        .map_err(|e| e.to_string())?;
    engine
        .download_multi_connection_with_resume(request, tx, control, persisted)
        .await
}

async fn merge_parts(temp_dir: &Path, part_count: usize, output_file: &Path) -> Result<(), String> {
    let mut destination = OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(output_file)
        .await
        .map_err(|e| e.to_string())?;

    for idx in 0..part_count {
        let part_path = temp_dir.join(format!("part-{idx}.bin"));
        let mut source = OpenOptions::new().read(true).open(&part_path).await.map_err(|e| e.to_string())?;
        let mut buffer = vec![0_u8; 64 * 1024];
        loop {
            let read = source.read(&mut buffer).await.map_err(|e| e.to_string())?;
            if read == 0 {
                break;
            }
            destination.write_all(&buffer[..read]).await.map_err(|e| e.to_string())?;
        }
    }
    destination.flush().await.map_err(|e| e.to_string())
}

async fn verify_parts_complete(temp_dir: &Path, plans: &[ChunkPlan]) -> Result<(), String> {
    for (idx, plan) in plans.iter().enumerate() {
        let part_path = temp_dir.join(format!("part-{idx}.bin"));
        let expected = plan.end_inclusive - plan.start + 1;
        let actual = existing_size(&part_path).await.map_err(|e| e.to_string())?;
        if actual != expected {
            return Err(format!(
                "Part {idx} size mismatch before merge: expected {expected}, got {actual}"
            ));
        }
    }
    Ok(())
}

async fn emit_control_failure(tx: &mpsc::Sender<DownloadEvent>, id: &DownloadId, reason: &str) {
    if reason == "Download cancelled" {
        let _ = tx.send(DownloadEvent::Cancelled { id: id.clone() }).await;
    } else {
        let _ = tx
            .send(DownloadEvent::Failed {
                id: id.clone(),
                reason: reason.to_string(),
            })
            .await;
    }
}

async fn existing_size(path: &Path) -> std::io::Result<u64> {
    match fs::metadata(path).await {
        Ok(metadata) => Ok(metadata.len()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(0),
        Err(error) => Err(error),
    }
}

async fn wait_if_paused_or_cancelled(control: &DownloadControl) -> Result<(), String> {
    if control.is_cancelled() {
        return Err("Download cancelled".to_string());
    }
    while control.is_paused() {
        if control.is_cancelled() {
            return Err("Download cancelled".to_string());
        }
        tokio::time::sleep(Duration::from_millis(150)).await;
    }
    Ok(())
}

async fn prepare_resume_chunks(
    persisted: &[ChunkProgress],
    plans: &[ChunkPlan],
    temp_dir: &Path,
) -> Vec<ChunkProgress> {
    let mut out = Vec::new();
    for (idx, plan) in plans.iter().enumerate() {
        let expected_max = plan.end_inclusive - plan.start + 1;
        let db_downloaded = persisted
            .iter()
            .find(|c| c.chunk_index == idx as i64)
            .map(|c| c.downloaded)
            .unwrap_or(0);
        let part_path = temp_dir.join(format!("part-{idx}.bin"));
        let disk_size = existing_size(&part_path).await.unwrap_or(0);
        let safe = db_downloaded.min(disk_size).min(expected_max);
        out.push(ChunkProgress {
            download_id: String::new(),
            chunk_index: idx as i64,
            start: plan.start,
            end_inclusive: plan.end_inclusive,
            downloaded: safe,
        });
    }
    out
}

async fn send_with_retry<F>(make_request: F, attempts: usize) -> Result<reqwest::Response, String>
where
    F: Fn() -> reqwest::RequestBuilder,
{
    let mut retry = 0;
    loop {
        match make_request().send().await {
            Ok(response) if response.status().is_success() || response.status() == reqwest::StatusCode::PARTIAL_CONTENT => return Ok(response),
            Ok(response) => {
                retry += 1;
                if retry >= attempts {
                    return Err(format!("HTTP failed with status {}", response.status()));
                }
            }
            Err(error) => {
                retry += 1;
                if retry >= attempts {
                    return Err(error.to_string());
                }
            }
        }
        tokio::time::sleep(Duration::from_millis(300 * retry as u64)).await;
    }
}

fn request_header_map(request: Option<&DownloadRequest>) -> HeaderMap {
    let mut headers = HeaderMap::new();
    headers.insert(ACCEPT_ENCODING, HeaderValue::from_static("identity"));
    headers.insert(
        USER_AGENT,
        HeaderValue::from_static("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"),
    );

    if let Some(request) = request {
        for (key, value) in &request.headers {
            if key.eq_ignore_ascii_case("host") {
                continue;
            }
            if let (Ok(name), Ok(value)) = (
                HeaderName::from_bytes(key.as_bytes()),
                HeaderValue::from_str(value),
            ) {
                headers.insert(name, value);
            }
        }
        if let Some(referrer) = &request.referrer {
            if let Ok(value) = HeaderValue::from_str(referrer) {
                headers.insert(REFERER, value);
            }
        }
        if let Some(cookies) = &request.cookies {
            if let Ok(value) = HeaderValue::from_str(cookies) {
                headers.insert(HeaderName::from_static("cookie"), value);
            }
        }
        if let Some(user_agent) = &request.user_agent {
            if let Ok(value) = HeaderValue::from_str(user_agent) {
                headers.insert(USER_AGENT, value);
            }
        }
    }

    headers
}

fn apply_header_map(builder: reqwest::RequestBuilder, headers: &HeaderMap) -> reqwest::RequestBuilder {
    headers.iter().fold(builder, |builder, (name, value)| builder.header(name, value))
}

fn apply_credentials(
    builder: reqwest::RequestBuilder,
    username: Option<&str>,
    password: Option<&str>,
) -> reqwest::RequestBuilder {
    match (username, password) {
        (Some(username), Some(password)) if !username.is_empty() && !password.is_empty() => {
            builder.basic_auth(username, Some(password))
        }
        _ => builder,
    }
}

fn client_for_request(request: &DownloadRequest) -> Option<reqwest::Client> {
    let proxy_url = request.proxy_url.as_ref()?;
    let mut proxy = reqwest::Proxy::all(proxy_url).ok()?;
    if let (Some(username), Some(password)) = (&request.proxy_username, &request.proxy_password) {
        proxy = proxy.basic_auth(username, password);
    }
    reqwest::Client::builder().proxy(proxy).build().ok()
}

fn validate_range_response(response: &reqwest::Response, expected_start: u64, expected_end: u64) -> Result<(), String> {
    if response.status() != reqwest::StatusCode::PARTIAL_CONTENT {
        return Err(format!(
            "Server did not honor range request: expected 206, got {}",
            response.status()
        ));
    }
    let expected_length = expected_end - expected_start + 1;
    if let Some((start, end, _total)) = response
        .headers()
        .get(CONTENT_RANGE)
        .and_then(|v| v.to_str().ok())
        .and_then(parse_content_range)
    {
        if start != expected_start || end != expected_end {
            return Err(format!(
                "Server returned unexpected content range: expected {expected_start}-{expected_end}, got {start}-{end}"
            ));
        }
    }
    if let Some(content_length) = response
        .headers()
        .get(CONTENT_LENGTH)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse::<u64>().ok())
    {
        if content_length != expected_length {
            return Err(format!(
                "Server returned unexpected range length: expected {expected_length}, got {content_length}"
            ));
        }
    }
    Ok(())
}

fn parse_content_range(value: &str) -> Option<(u64, u64, Option<u64>)> {
    let value = value.strip_prefix("bytes ")?;
    let (range, total) = value.split_once('/')?;
    let (start, end) = range.split_once('-')?;
    let total = if total == "*" { None } else { total.parse().ok() };
    Some((start.parse().ok()?, end.parse().ok()?, total))
}

fn extract_filename_from_response(headers: &HeaderMap) -> Option<String> {
    headers
        .get(CONTENT_DISPOSITION)
        .and_then(|value| value.to_str().ok())
        .and_then(extract_filename_from_content_disposition)
}

fn extract_filename_from_content_disposition(value: &str) -> Option<String> {
    for part in value.split(';').map(str::trim) {
        let lower = part.to_ascii_lowercase();
        if lower.starts_with("filename*=") {
            let raw = part.split_once('=')?.1.trim_matches('"');
            let filename = raw.rsplit_once("''").map(|(_, v)| v).unwrap_or(raw);
            return sanitize_filename(filename);
        }
        if lower.starts_with("filename=") {
            let raw = part.split_once('=')?.1.trim_matches('"');
            return sanitize_filename(raw);
        }
    }
    None
}

fn sanitize_filename(value: &str) -> Option<String> {
    let filename: String = value
        .chars()
        .map(|ch| match ch {
            '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' => '_',
            _ => ch,
        })
        .collect();
    if filename.trim().is_empty() { None } else { Some(filename) }
}

fn uuid_like_seed(input: &str) -> u64 {
    let mut hash = 1469598103934665603_u64;
    for byte in input.as_bytes() {
        hash ^= u64::from(*byte);
        hash = hash.wrapping_mul(1099511628211);
    }
    hash
}
