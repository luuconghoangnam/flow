use std::path::PathBuf;

pub fn flow_data_dir() -> PathBuf {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")));
    let dir = base.join("Flow");
    let _ = std::fs::create_dir_all(&dir);
    dir
}

pub fn flow_db_path() -> PathBuf {
    flow_data_dir().join("flow.db")
}

pub fn flow_signal_path() -> PathBuf {
    flow_data_dir().join("queue.signal")
}

pub fn flow_clipboard_pending_path() -> PathBuf {
    flow_data_dir().join("clipboard.pending.json")
}

pub fn flow_clipboard_decision_path() -> PathBuf {
    flow_data_dir().join("clipboard.decision.json")
}
