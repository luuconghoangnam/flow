pub mod download;
pub mod hls;
pub mod model;
pub mod paths;
pub mod persistence;
pub mod queue;
pub mod settings;
pub mod storage;
pub mod startup;

pub use download::{
    run_multi_connection_with_repository, ChunkPlan, DownloadControl, DownloadEngine,
    DownloadEvent, DownloadRequest,
};
pub use model::{
    transition_state, DownloadCommand, DownloadId, DownloadStatus, DownloadTask, LifecycleState,
};
pub use persistence::persist_chunk_progress_worker;
pub use paths::{flow_clipboard_decision_path, flow_clipboard_pending_path, flow_data_dir, flow_db_path, flow_signal_path};
pub use queue::{pause_active_job, resume_active_job, QueueScheduler};
pub use settings::{load_settings, per_host_for_url, save_settings, should_use_manual_proxy, FlowSettings, ManualProxy, PerHostSettings, ProxyMode, ProxySettings};
pub use storage::{ChunkProgress, DownloadRepository, QueueGroupRecord, QueueJobRecord, QueueRuntimeEvent, QueueViewRow, SqliteDownloadRepository};
pub use startup::{is_windows_auto_start_enabled, set_windows_auto_start};

pub fn init() {
    println!("Flow core initialized.");
}
