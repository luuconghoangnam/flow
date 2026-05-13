#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum DownloadActivity {
    Downloading,
    Queued,
    Paused,
    Failed,
    Finished,
    Other,
}

pub(crate) fn classify_download_activity(status: &str) -> DownloadActivity {
    let normalized = status.trim().to_ascii_lowercase();
    if normalized.contains("downloading") {
        DownloadActivity::Downloading
    } else if normalized.contains("queued") || normalized.contains("starting") || normalized.contains("connecting") {
        DownloadActivity::Queued
    } else if normalized.contains("paused") || normalized.contains("stopped") {
        DownloadActivity::Paused
    } else if normalized.contains("failed") || normalized.contains("error") {
        DownloadActivity::Failed
    } else if normalized.contains("finished") || normalized.contains("completed") {
        DownloadActivity::Finished
    } else {
        DownloadActivity::Other
    }
}

pub(crate) fn is_finished_status(status: &str) -> bool {
    matches!(classify_download_activity(status), DownloadActivity::Finished)
}

pub(crate) fn is_pausable_status(status: &str) -> bool {
    matches!(classify_download_activity(status), DownloadActivity::Downloading | DownloadActivity::Queued)
}

pub(crate) fn is_resumable_status(status: &str) -> bool {
    matches!(classify_download_activity(status), DownloadActivity::Paused | DownloadActivity::Failed)
}

pub(crate) fn is_requeueable_status(status: &str) -> bool {
    matches!(
        classify_download_activity(status),
        DownloadActivity::Paused | DownloadActivity::Failed | DownloadActivity::Finished | DownloadActivity::Queued
    )
}

pub(crate) fn is_editable_status(status: &str) -> bool {
    !is_pausable_status(status)
}
