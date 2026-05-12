use std::path::PathBuf;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct DownloadId(pub String);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DownloadStatus {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
}

#[derive(Debug, Clone)]
pub struct DownloadTask {
    pub id: DownloadId,
    pub url: String,
    pub output_path: PathBuf,
    pub file_name: String,
    pub total_bytes: Option<u64>,
    pub downloaded_bytes: u64,
    pub status: DownloadStatus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DownloadCommand {
    Start,
    Pause,
    Resume,
    Cancel,
    Retry,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LifecycleState {
    Idle,
    Running,
    Paused,
    Cancelled,
    Failed,
    Completed,
}

pub fn transition_state(current: LifecycleState, command: DownloadCommand) -> LifecycleState {
    match (current, command) {
        (LifecycleState::Idle, DownloadCommand::Start) => LifecycleState::Running,
        (LifecycleState::Running, DownloadCommand::Pause) => LifecycleState::Paused,
        (LifecycleState::Paused, DownloadCommand::Resume) => LifecycleState::Running,
        (LifecycleState::Running, DownloadCommand::Cancel) => LifecycleState::Cancelled,
        (LifecycleState::Paused, DownloadCommand::Cancel) => LifecycleState::Cancelled,
        (LifecycleState::Failed, DownloadCommand::Retry) => LifecycleState::Running,
        (state, _) => state,
    }
}
