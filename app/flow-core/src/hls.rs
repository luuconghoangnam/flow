use std::collections::VecDeque;
use std::path::Path;
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use futures_util::StreamExt;
use reqwest::header::CONTENT_TYPE;
use reqwest::Url;
use tokio::fs::{self, OpenOptions};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::sync::{Mutex, mpsc};
use tokio::task::JoinSet;

use crate::download::{
    DownloadControl, DownloadEngine, DownloadEvent, DownloadRequest, SpeedLimiter,
};
use crate::model::{DownloadStatus, DownloadTask};

const HLS_CONTENT_TYPES: [&str; 2] = [
    "application/x-mpegurl",
    "application/vnd.apple.mpegurl",
];

#[derive(Clone, Debug)]
struct HlsSegment {
    index: usize,
    url: String,
}

#[derive(Clone, Debug)]
struct VariantStream {
    uri: String,
    bandwidth: u64,
}

pub fn is_hls_request(url: &str, file_name: &str, headers: Option<&reqwest::header::HeaderMap>) -> bool {
    if url.to_ascii_lowercase().contains(".m3u8") || file_name.to_ascii_lowercase().ends_with(".m3u8") {
        return true;
    }
    headers
        .and_then(|value| value.get(CONTENT_TYPE))
        .and_then(|value| value.to_str().ok())
        .map(|value| {
            let lower = value.to_ascii_lowercase();
            HLS_CONTENT_TYPES.iter().any(|candidate| lower.starts_with(candidate))
        })
        .unwrap_or(false)
}

pub async fn download_hls(
    engine: &DownloadEngine,
    request: DownloadRequest,
    tx: mpsc::Sender<DownloadEvent>,
    control: DownloadControl,
) -> Result<DownloadTask, String> {
    let mut task = engine.enqueue(request.clone()).await;
    let _ = tx.send(DownloadEvent::Queued(task.clone())).await;
    fs::create_dir_all(&request.output_dir).await.map_err(|e| e.to_string())?;

    let playlist_text = fetch_text(engine, &request, &request.url).await?;
    let media_playlist_url = resolve_media_playlist_url(&request.url, &playlist_text)?;
    let media_playlist_text = if media_playlist_url == request.url {
        playlist_text
    } else {
        fetch_text(engine, &request, &media_playlist_url).await?
    };
    let segments = parse_media_playlist(&media_playlist_url, &media_playlist_text)?;
    if segments.is_empty() {
        return Err("playlist has no segments".to_string());
    }

    let output_name = suggested_output_name(&request.file_name, &request.url);
    let output_file = unique_output_path(&request.output_dir.join(output_name));
    let temp_dir = request.output_dir.join(format!(".{}.hls", task.id.0));
    fs::create_dir_all(&temp_dir).await.map_err(|e| e.to_string())?;

    task.status = DownloadStatus::Downloading;
    task.file_name = output_file
        .file_name()
        .and_then(|v| v.to_str())
        .unwrap_or("download.ts")
        .to_string();
    task.total_bytes = None;

    let existing_total = sum_existing_segment_bytes(&temp_dir, &segments).await?;
    let aggregate = Arc::new(AtomicU64::new(existing_total));
    let work_queue = Arc::new(Mutex::new(VecDeque::from(segments.clone())));
    let limiter = request.speed_limit_bps.filter(|v| *v > 0).map(SpeedLimiter::new);
    let worker_count = request.connections.max(1).min(segments.len().max(1));
    let task_id = task.id.clone();
    let mut join_set = JoinSet::new();

    for _ in 0..worker_count {
        let engine_client = engine.client();
        let request_clone = request.clone();
        let control2 = control.clone();
        let tx2 = tx.clone();
        let task_id2 = task_id.clone();
        let aggregate2 = Arc::clone(&aggregate);
        let temp_dir2 = temp_dir.clone();
        let queue2 = Arc::clone(&work_queue);
        let limiter2 = limiter.clone();
        let global_limiter = engine.global_limiter.clone();

        join_set.spawn(async move {
            loop {
                let segment = {
                    let mut queue = queue2.lock().await;
                    queue.pop_front()
                };
                let Some(segment) = segment else {
                    return Ok::<(), String>(());
                };

                let part_path = temp_dir2.join(format!("segment-{:06}.ts", segment.index));
                let existing = existing_size(&part_path).await.map_err(|e| e.to_string())?;
                let response = build_segment_request(&engine_client, &request_clone, &segment.url, existing)
                    .send()
                    .await
                    .map_err(|e| e.to_string())?;
                if existing > 0 && response.status() != reqwest::StatusCode::PARTIAL_CONTENT {
                    return Err(format!("Server did not resume HLS segment {}", segment.url));
                }
                if existing == 0 && !response.status().is_success() {
                    return Err(format!("HTTP request failed for HLS segment {} with status {}", segment.url, response.status()));
                }
                let mut stream = response.bytes_stream();
                let mut file = OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(&part_path)
                    .await
                    .map_err(|e| e.to_string())?;
                while let Some(chunk) = stream.next().await {
                    wait_if_paused_or_cancelled(&control2).await?;
                    let bytes = chunk.map_err(|e| e.to_string())?;
                    if let Some(limiter) = &limiter2 {
                        limiter.consume(bytes.len()).await;
                    }
                    if let Some(global) = &global_limiter {
                        global.consume(bytes.len()).await;
                    }
                    file.write_all(&bytes).await.map_err(|e| e.to_string())?;
                    let total_done = aggregate2.fetch_add(bytes.len() as u64, Ordering::SeqCst) + bytes.len() as u64;
                    let _ = tx2
                        .send(DownloadEvent::Progress {
                            id: task_id2.clone(),
                            downloaded_bytes: total_done,
                            total_bytes: None,
                        })
                        .await;
                }
            }
        });
    }

    while let Some(result) = join_set.join_next().await {
        match result.map_err(|e| e.to_string())? {
            Ok(()) => {}
            Err(reason) => {
                let _ = tx.send(DownloadEvent::Failed { id: task.id.clone(), reason: reason.clone() }).await;
                return Err(reason);
            }
        }
    }

    merge_segments(&temp_dir, segments.len(), &output_file).await?;
    let _ = fs::remove_dir_all(&temp_dir).await;
    task.status = DownloadStatus::Completed;
    task.downloaded_bytes = existing_size(&output_file).await.map_err(|e| e.to_string())?;
    task.total_bytes = Some(task.downloaded_bytes);
    let _ = tx.send(DownloadEvent::Completed(task.clone())).await;
    Ok(task)
}

fn resolve_media_playlist_url(base_url: &str, playlist_text: &str) -> Result<String, String> {
    if !playlist_text.contains("#EXT-X-STREAM-INF") {
        return Ok(base_url.to_string());
    }
    let variants = parse_master_playlist(base_url, playlist_text)?;
    variants
        .into_iter()
        .max_by_key(|variant| variant.bandwidth)
        .map(|variant| variant.uri)
        .ok_or_else(|| "master playlist has no variants".to_string())
}

fn parse_master_playlist(base_url: &str, playlist_text: &str) -> Result<Vec<VariantStream>, String> {
    let mut variants = Vec::new();
    let mut pending_bandwidth = None::<u64>;
    for line in playlist_text.lines().map(str::trim).filter(|line| !line.is_empty()) {
        if let Some(rest) = line.strip_prefix("#EXT-X-STREAM-INF:") {
            pending_bandwidth = extract_attribute(rest, "BANDWIDTH").and_then(|value| value.parse::<u64>().ok());
            continue;
        }
        if line.starts_with('#') {
            continue;
        }
        if let Some(bandwidth) = pending_bandwidth.take() {
            variants.push(VariantStream {
                uri: resolve_relative_url(base_url, line)?,
                bandwidth,
            });
        }
    }
    Ok(variants)
}

fn parse_media_playlist(base_url: &str, playlist_text: &str) -> Result<Vec<HlsSegment>, String> {
    let mut segments = Vec::new();
    let mut saw_playlist = false;
    for line in playlist_text.lines().map(str::trim).filter(|line| !line.is_empty()) {
        if line == "#EXTM3U" {
            saw_playlist = true;
            continue;
        }
        if let Some(rest) = line.strip_prefix("#EXT-X-KEY:") {
            let method = extract_attribute(rest, "METHOD").unwrap_or_default();
            if !method.eq_ignore_ascii_case("NONE") {
                return Err("Encrypted HLS playlists are not supported yet".to_string());
            }
            continue;
        }
        if line.starts_with('#') {
            continue;
        }
        let segment_url = resolve_relative_url(base_url, line)?;
        let extension = Path::new(segment_url.split('?').next().unwrap_or(""))
            .extension()
            .and_then(|value| value.to_str())
            .unwrap_or_default()
            .to_ascii_lowercase();
        if extension != "ts" {
            return Err(format!("Only HLS .ts segments supported at the moment, but '{extension}' provided"));
        }
        segments.push(HlsSegment { index: segments.len(), url: segment_url });
    }
    if !saw_playlist {
        return Err("invalid HLS playlist".to_string());
    }
    Ok(segments)
}

async fn fetch_text(engine: &DownloadEngine, request: &DownloadRequest, url: &str) -> Result<String, String> {
    let response = build_segment_request(&engine.client(), request, url, 0)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !response.status().is_success() {
        return Err(format!("HTTP request failed with status {}", response.status()));
    }
    response.text().await.map_err(|e| e.to_string())
}

fn build_segment_request(
    client: &reqwest::Client,
    request: &DownloadRequest,
    url: &str,
    existing: u64,
) -> reqwest::RequestBuilder {
    let mut builder = client.get(url);
    for (key, value) in &request.headers {
        if let Ok(name) = reqwest::header::HeaderName::from_bytes(key.as_bytes()) {
            if let Ok(value) = reqwest::header::HeaderValue::from_str(value) {
                builder = builder.header(name, value);
            }
        }
    }
    if let Some(referrer) = &request.referrer {
        builder = builder.header(reqwest::header::REFERER, referrer);
    }
    if let Some(cookies) = &request.cookies {
        builder = builder.header(reqwest::header::COOKIE, cookies);
    }
    if let Some(user_agent) = &request.user_agent {
        builder = builder.header(reqwest::header::USER_AGENT, user_agent);
    }
    if existing > 0 {
        builder = builder.header(reqwest::header::RANGE, format!("bytes={existing}-"));
    }
    match (&request.username, &request.password) {
        (Some(username), Some(password)) if !username.is_empty() && !password.is_empty() => {
            builder.basic_auth(username, Some(password))
        }
        _ => builder,
    }
}

fn resolve_relative_url(base_url: &str, target: &str) -> Result<String, String> {
    let base = Url::parse(base_url).map_err(|e| e.to_string())?;
    base.join(target).map(|value| value.to_string()).map_err(|e| e.to_string())
}

fn extract_attribute(attributes: &str, key: &str) -> Option<String> {
    for part in attributes.split(',') {
        let (name, value) = part.split_once('=')?;
        if name.trim().eq_ignore_ascii_case(key) {
            return Some(value.trim().trim_matches('"').to_string());
        }
    }
    None
}

fn suggested_output_name(file_name: &str, url: &str) -> String {
    if !file_name.trim().is_empty() && !file_name.eq_ignore_ascii_case("download.bin") {
        return file_name.trim_end_matches(".m3u8").to_string() + ".ts";
    }
    let fallback = url
        .split('/')
        .next_back()
        .unwrap_or("stream.m3u8")
        .split('?')
        .next()
        .unwrap_or("stream.m3u8");
    fallback.trim_end_matches(".m3u8").to_string() + ".ts"
}

async fn merge_segments(temp_dir: &Path, segment_count: usize, output_file: &Path) -> Result<(), String> {
    let mut destination = OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(output_file)
        .await
        .map_err(|e| e.to_string())?;
    for idx in 0..segment_count {
        let path = temp_dir.join(format!("segment-{:06}.ts", idx));
        let mut source = OpenOptions::new().read(true).open(&path).await.map_err(|e| e.to_string())?;
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

async fn existing_size(path: &Path) -> std::io::Result<u64> {
    match fs::metadata(path).await {
        Ok(metadata) => Ok(metadata.len()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(0),
        Err(error) => Err(error),
    }
}

async fn sum_existing_segment_bytes(temp_dir: &Path, segments: &[HlsSegment]) -> Result<u64, String> {
    let mut total = 0_u64;
    for segment in segments {
        let path = temp_dir.join(format!("segment-{:06}.ts", segment.index));
        total += existing_size(&path).await.map_err(|e| e.to_string())?;
    }
    Ok(total)
}

fn unique_output_path(path: &Path) -> std::path::PathBuf {
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
