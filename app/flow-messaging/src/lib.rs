use std::io::{Read, Write};
use std::io::{stdin, stdout};
use std::collections::BTreeMap;

use byteorder::{LittleEndian, ReadBytesExt, WriteBytesExt};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Serialize, Deserialize)]
pub struct BrowserDownloadMessage {
    pub url: String,
    pub file_name: Option<String>,
    pub output_dir: Option<String>,
    pub connections: Option<usize>,
    pub headers: Option<BTreeMap<String, String>>,
    pub referrer: Option<String>,
    pub cookies: Option<String>,
    pub user_agent: Option<String>,
    pub username: Option<String>,
    pub password: Option<String>,
    pub priority: Option<i64>,
    pub queue_id: Option<i64>,
    pub expected_sha256_hex: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DownloadStatusRequest {
    pub download_id: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DownloadRetryRequest {
    pub download_id: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DownloadControlRequest {
    pub download_id: String,
    pub queue_id: Option<i64>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct QueueGroupRequest {
    pub queue_id: Option<i64>,
    pub name: Option<String>,
    pub max_concurrent: Option<i64>,
    pub stop_on_empty: Option<bool>,
    pub active: Option<bool>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct QueueJobOrderRequest {
    pub download_id: String,
    pub direction: Option<String>,
    pub target_index: Option<usize>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct QueueEventQueryRequest {
    pub limit: Option<usize>,
}

#[derive(Debug, Serialize, Deserialize, Default)]
pub struct DownloadCleanupRequest {
    pub statuses: Option<Vec<String>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ExtensionEnvelope<T> {
    pub version: u16,
    pub message_type: String,
    pub payload: T,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct HostAck {
    pub status_code: u16,
    pub message: String,
    pub download_id: Option<String>,
    pub data: Option<Value>,
}

impl<T> ExtensionEnvelope<T> {
    pub fn new(message_type: impl Into<String>, payload: T) -> Self {
        Self {
            version: 1,
            message_type: message_type.into(),
            payload,
        }
    }
}

pub fn parse_browser_download_envelope(
    json: &str,
) -> Result<ExtensionEnvelope<BrowserDownloadMessage>, serde_json::Error> {
    serde_json::from_str(json)
}

pub fn parse_command_envelope(json: &str) -> Result<ExtensionEnvelope<Value>, serde_json::Error> {
    serde_json::from_str(json)
}

pub fn read_native_message(input: &mut impl Read) -> std::io::Result<String> {
    let size = input.read_u32::<LittleEndian>()? as usize;
    let mut buffer = vec![0_u8; size];
    input.read_exact(&mut buffer)?;
    Ok(String::from_utf8_lossy(&buffer).to_string())
}

pub fn write_native_message(output: &mut impl Write, payload: &str) -> std::io::Result<()> {
    output.write_u32::<LittleEndian>(payload.len() as u32)?;
    output.write_all(payload.as_bytes())?;
    output.flush()
}

pub struct HostCommandHandlers<Create, Status, List, Retry, Cleanup, Start, Stop, QueueList, QueueCreate, QueueUpdate, QueueDelete, QueueMove, QueueRequeue, QueueSwap, QueueEvents> {
    pub create: Create,
    pub status: Status,
    pub list: List,
    pub retry: Retry,
    pub cleanup: Cleanup,
    pub start: Start,
    pub stop: Stop,
    pub queue_list: QueueList,
    pub queue_create: QueueCreate,
    pub queue_update: QueueUpdate,
    pub queue_delete: QueueDelete,
    pub queue_move: QueueMove,
    pub queue_requeue: QueueRequeue,
    pub queue_swap: QueueSwap,
    pub queue_events: QueueEvents,
}

pub fn run_native_host_loop<Create, Status, List, Retry, Cleanup, Start, Stop, QueueList, QueueCreate, QueueUpdate, QueueDelete, QueueMove, QueueRequeue, QueueSwap, QueueEvents>(mut handlers: HostCommandHandlers<Create, Status, List, Retry, Cleanup, Start, Stop, QueueList, QueueCreate, QueueUpdate, QueueDelete, QueueMove, QueueRequeue, QueueSwap, QueueEvents>) -> Result<(), String>
where
    Create: FnMut(BrowserDownloadMessage) -> Result<Option<String>, String>,
    Status: FnMut(DownloadStatusRequest) -> Result<Value, String>,
    List: FnMut(()) -> Result<Value, String>,
    Retry: FnMut(DownloadRetryRequest) -> Result<Option<String>, String>,
    Cleanup: FnMut(DownloadCleanupRequest) -> Result<Value, String>,
    Start: FnMut(DownloadControlRequest) -> Result<Option<String>, String>,
    Stop: FnMut(DownloadControlRequest) -> Result<Option<String>, String>,
    QueueList: FnMut(()) -> Result<Value, String>,
    QueueCreate: FnMut(QueueGroupRequest) -> Result<Value, String>,
    QueueUpdate: FnMut(QueueGroupRequest) -> Result<Value, String>,
    QueueDelete: FnMut(QueueGroupRequest) -> Result<Value, String>,
    QueueMove: FnMut(QueueJobOrderRequest) -> Result<Value, String>,
    QueueRequeue: FnMut(QueueJobOrderRequest) -> Result<Value, String>,
    QueueSwap: FnMut(QueueJobOrderRequest) -> Result<Value, String>,
    QueueEvents: FnMut(QueueEventQueryRequest) -> Result<Value, String>,
{
    let mut input = stdin();
    let mut output = stdout();

    loop {
        let frame = match read_native_message(&mut input) {
            Ok(value) => value,
            Err(error) if error.kind() == std::io::ErrorKind::UnexpectedEof => break,
            Err(error) => return Err(error.to_string()),
        };

        let envelope = parse_command_envelope(&frame).map_err(|e| e.to_string())?;
        let result: Result<(u16, String, Option<String>, Option<Value>), String> = match envelope.message_type.as_str() {
            "download.create" => {
                let payload: BrowserDownloadMessage = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.create)(payload).map(|download_id| (200, "accepted".to_string(), download_id, None))
            }
            "download.status" => {
                let payload: DownloadStatusRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.status)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "download.list" => (handlers.list)(()).map(|data| (200, "ok".to_string(), None, Some(data))),
            "download.retry" => {
                let payload: DownloadRetryRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.retry)(payload).map(|download_id| (200, "accepted".to_string(), download_id, None))
            }
            "download.cleanup" => {
                let payload: DownloadCleanupRequest = serde_json::from_value(envelope.payload).unwrap_or_default();
                (handlers.cleanup)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "download.start" => {
                let payload: DownloadControlRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.start)(payload).map(|download_id| (200, "accepted".to_string(), download_id, None))
            }
            "download.stop" => {
                let payload: DownloadControlRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.stop)(payload).map(|download_id| (200, "accepted".to_string(), download_id, None))
            }
            "queue.list" => (handlers.queue_list)(()).map(|data| (200, "ok".to_string(), None, Some(data))),
            "queue.create" => {
                let payload: QueueGroupRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.queue_create)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "queue.update" => {
                let payload: QueueGroupRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.queue_update)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "queue.delete" => {
                let payload: QueueGroupRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.queue_delete)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "queue.move" => {
                let payload: QueueJobOrderRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.queue_move)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "queue.requeue" => {
                let payload: QueueJobOrderRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.queue_requeue)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "queue.swap" => {
                let payload: QueueJobOrderRequest = serde_json::from_value(envelope.payload).map_err(|e| e.to_string())?;
                (handlers.queue_swap)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            "queue.events" => {
                let payload: QueueEventQueryRequest = serde_json::from_value(envelope.payload).unwrap_or(QueueEventQueryRequest { limit: Some(50) });
                (handlers.queue_events)(payload).map(|data| (200, "ok".to_string(), None, Some(data)))
            }
            other => Ok((400, format!("Unknown command: {other}"), None, None)),
        };

        match result {
            Ok((status_code, message, download_id, data)) => {
                let ack = HostAck {
                    status_code,
                    message,
                    download_id,
                    data,
                };
                write_native_message(&mut output, &serde_json::to_string(&ack).map_err(|e| e.to_string())?)
                    .map_err(|e| e.to_string())?;
            }
            Err(reason) => {
                let (status_code, message) = if let Some(message) = reason.strip_prefix("BAD_REQUEST:") {
                    (400, message.trim().to_string())
                } else {
                    (500, reason)
                };
                let ack = HostAck {
                    status_code,
                    message,
                    download_id: None,
                    data: None,
                };
                write_native_message(&mut output, &serde_json::to_string(&ack).map_err(|e| e.to_string())?)
                    .map_err(|e| e.to_string())?;
            }
        }
    }

    Ok(())
}
