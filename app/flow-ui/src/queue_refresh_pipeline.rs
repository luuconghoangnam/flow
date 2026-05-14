use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime};

use slint::{ModelRc, VecModel};

use crate::download_row_vm::{apply_queue_refresh_derived, build_queue_refresh_derived, current_queue_refresh_derived, QueueRefreshRequest};
use crate::home_action_menu_presentation::apply_downloads_menu_presentation;
use crate::{
    format_bytes, load_queue_ui_state, CategoryFilter, DownloadsMenuPresentation, MainWindow,
    ProgressSample, QueueUiState, SortState,
};

pub(crate) static DOWNLOAD_PROGRESS_CACHE: std::sync::LazyLock<Mutex<HashMap<String, ProgressSample>>> =
    std::sync::LazyLock::new(|| Mutex::new(HashMap::new()));
pub(crate) static LAST_REFRESH_AT_MS: AtomicU64 = AtomicU64::new(0);
pub(crate) static REFRESH_IN_FLIGHT: AtomicBool = AtomicBool::new(false);
pub(crate) static LAST_QUEUE_SNAPSHOT: std::sync::LazyLock<Mutex<Option<QueueUiState>>> =
    std::sync::LazyLock::new(|| Mutex::new(None));
pub(crate) static QUEUE_REFRESH_REQUEST: std::sync::LazyLock<Mutex<Option<QueueRefreshRequest>>> =
    std::sync::LazyLock::new(|| Mutex::new(None));
pub(crate) static QUEUE_REFRESH_DIRTY: AtomicBool = AtomicBool::new(false);

pub(crate) fn enqueue_queue_refresh(
    weak: &slint::Weak<MainWindow>,
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) {
    let request = QueueRefreshRequest {
        weak: weak.clone(),
        queue_id,
        selected_download,
        checked_downloads,
        sort_state: Arc::clone(sort_state),
        category_filter: Arc::clone(category_filter),
    };
    if let Ok(mut slot) = QUEUE_REFRESH_REQUEST.lock() {
        *slot = Some(request);
    }
    QUEUE_REFRESH_DIRTY.store(true, Ordering::Release);
    if REFRESH_IN_FLIGHT
        .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
        .is_err()
    {
        return;
    }
    std::thread::spawn(move || loop {
        std::thread::sleep(Duration::from_millis(350));
        QUEUE_REFRESH_DIRTY.store(false, Ordering::Release);
        let request = QUEUE_REFRESH_REQUEST.lock().ok().and_then(|guard| guard.clone());
        let Some(request) = request else {
            REFRESH_IN_FLIGHT.store(false, Ordering::Release);
            return;
        };
        let derived = build_queue_refresh_derived(
            request.queue_id,
            request.selected_download.clone(),
            request.checked_downloads.clone(),
            &request.sort_state,
            &request.category_filter,
        );
        if let Ok(mut guard) = LAST_QUEUE_SNAPSHOT.lock() {
            *guard = Some(derived.state.clone());
        }
        let _ = apply_queue_refresh_derived(&request.weak, derived);
        if !QUEUE_REFRESH_DIRTY.load(Ordering::Acquire) {
            REFRESH_IN_FLIGHT.store(false, Ordering::Release);
            if !QUEUE_REFRESH_DIRTY.swap(false, Ordering::AcqRel) {
                return;
            }
            if REFRESH_IN_FLIGHT
                .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
                .is_err()
            {
                return;
            }
        }
    });
}

pub(crate) fn refresh_queue_ui(
    weak: &slint::Weak<MainWindow>,
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<std::collections::HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> DownloadsMenuPresentation {
    let now_ms = now_refresh_ms();
    let last_ms = LAST_REFRESH_AT_MS.load(Ordering::Relaxed);
    let should_throttle = now_ms.saturating_sub(last_ms) < 500;
    let acquired_flight = REFRESH_IN_FLIGHT
        .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
        .is_ok();
    let in_flight = !acquired_flight;

    if should_throttle || in_flight {
        let _cached_state = LAST_QUEUE_SNAPSHOT
            .lock()
            .ok()
            .and_then(|guard| guard.clone())
            .unwrap_or_else(|| queue_state_fallback("Queue refresh throttled"));
    } else {
        LAST_REFRESH_AT_MS.store(now_ms, Ordering::Relaxed);
        let fresh = load_queue_ui_state(queue_id, sort_state, category_filter);
        if let Ok(mut guard) = LAST_QUEUE_SNAPSHOT.lock() {
            *guard = Some(fresh.clone());
        }
    }

    if acquired_flight {
        REFRESH_IN_FLIGHT.store(false, Ordering::Release);
    }
    let derived = current_queue_refresh_derived(
        queue_id,
        selected_download,
        checked_downloads,
        sort_state,
        category_filter,
    );
    let downloads_menu_for_ui = derived.downloads_menu.clone();
    let downloads_menu_for_return = downloads_menu_for_ui.clone();
    let scheduler_state = derived.scheduler_state.clone();
    let state = derived.state;
    let selected_queue_index = derived.selected_queue_index;
    let selected_index = derived.selected_index;
    let _ = weak.upgrade_in_event_loop(move |app| {
        app.set_selected_queue_index(selected_queue_index);
        app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
        app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
        app.set_queue_config_summary(state.queue_summary.into());
        app.set_queue_name_text(scheduler_state.queue_name.clone().into());
        app.set_selected_download_index(selected_index);
        apply_downloads_menu_presentation(&app, &downloads_menu_for_ui);
        app.set_queue_stop_on_empty(scheduler_state.stop_on_empty);
        app.set_queue_schedule_enabled(scheduler_state.enabled);
        app.set_queue_schedule_start(scheduler_state.start_text.into());
        app.set_queue_schedule_stop(scheduler_state.stop_text.into());
        app.set_footer_active_count(state.active_count);
        app.set_footer_speed_text(
            if state.active_speed_bytes_per_sec > 0 {
                format!("{}/s", format_bytes(state.active_speed_bytes_per_sec))
            } else {
                format_bytes(state.downloaded_bytes)
            }
            .into(),
        );
        app.set_footer_total_text(
            if state.total_bytes > 0 {
                format!("{} / {}", state.total_jobs, format_bytes(state.total_bytes))
            } else {
                state.total_jobs.to_string()
            }
            .into(),
        );
        app.set_queue_day_sun(scheduler_state.days[0]);
        app.set_queue_day_mon(scheduler_state.days[1]);
        app.set_queue_day_tue(scheduler_state.days[2]);
        app.set_queue_day_wed(scheduler_state.days[3]);
        app.set_queue_day_thu(scheduler_state.days[4]);
        app.set_queue_day_fri(scheduler_state.days[5]);
        app.set_queue_day_sat(scheduler_state.days[6]);
    });
    downloads_menu_for_return
}

pub(crate) fn estimate_speed_bytes_per_sec(
    cache: Option<&HashMap<String, ProgressSample>>,
    id: &str,
    downloaded_bytes: u64,
) -> Option<u64> {
    let sample = cache?.get(id)?;
    if downloaded_bytes <= sample.downloaded_bytes {
        return None;
    }
    let elapsed = SystemTime::now().duration_since(sample.observed_at).ok()?;
    if elapsed < Duration::from_millis(400) || elapsed > Duration::from_secs(20) {
        return None;
    }
    let delta_bytes = downloaded_bytes - sample.downloaded_bytes;
    let bytes_per_sec = (delta_bytes as f64 / elapsed.as_secs_f64()).round() as u64;
    (bytes_per_sec > 0).then_some(bytes_per_sec)
}

pub(crate) fn update_progress_cache(rows: &[flow_core::QueueViewRow]) {
    let now = SystemTime::now();
    if let Ok(mut cache) = DOWNLOAD_PROGRESS_CACHE.lock() {
        let visible_ids = rows.iter().map(|row| row.id.clone()).collect::<std::collections::HashSet<_>>();
        cache.retain(|id, _| visible_ids.contains(id));
        for row in rows {
            cache.insert(
                row.id.clone(),
                ProgressSample {
                    downloaded_bytes: row.downloaded_bytes,
                    observed_at: now,
                },
            );
        }
    }
}

pub(crate) fn queue_state_fallback(summary: &str) -> QueueUiState {
    QueueUiState {
        queue_labels: vec!["Main".into()],
        queue_ids: vec![0],
        rows: vec![],
        row_ids: vec![],
        queue_summary: summary.to_string(),
        active_count: 0,
        total_jobs: 0,
        downloaded_bytes: 0,
        total_bytes: 0,
        active_speed_bytes_per_sec: 0,
    }
}

pub(crate) fn now_refresh_ms() -> u64 {
    SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
