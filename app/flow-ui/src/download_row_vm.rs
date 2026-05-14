use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use slint::{ModelRc, VecModel};

use crate::{
    apply_checked_rows, derive_downloads_menu_presentation, derive_home_action_descriptors,
    derive_home_action_registry, derive_home_action_state, format_bytes,
    load_queue_scheduler_state, load_queue_ui_state, sync_selection_to_visible_rows,
    CategoryFilter, DownloadsMenuPresentation, HomeActionId, HomeActionRegistry,
    MainWindow, QueueSchedulerState, QueueUiState, SortState,
};
use crate::home_action_descriptors::HomeActionDescriptorState;
use crate::home_action_menu_presentation::apply_downloads_menu_presentation;
use crate::home_action_state::HomeActionState;
use crate::home_action_registry::HomeActionRegistry as RegistryAlias;
use crate::LAST_QUEUE_SNAPSHOT;

#[derive(Debug, Clone, Copy)]
pub(crate) struct ActionAvailability {
    pub(crate) can_open_selected: bool,
    pub(crate) can_open_selected_folder: bool,
    pub(crate) can_delete_selected: bool,
    pub(crate) can_resume_selected: bool,
    pub(crate) can_stop_selected: bool,
    pub(crate) can_move_selected_up: bool,
    pub(crate) can_move_selected_down: bool,
    pub(crate) can_requeue_selected: bool,
}

#[derive(Clone)]
pub(crate) struct QueueRefreshDerived {
    pub(crate) state: QueueUiState,
    pub(crate) selected_queue_index: i32,
    pub(crate) selected_index: i32,
    pub(crate) descriptors: HomeActionDescriptorState,
    pub(crate) registry: HomeActionRegistry,
    pub(crate) downloads_menu: DownloadsMenuPresentation,
    pub(crate) scheduler_state: QueueSchedulerState,
}

#[derive(Clone)]
pub(crate) struct QueueRefreshRequest {
    pub(crate) weak: slint::Weak<MainWindow>,
    pub(crate) queue_id: i64,
    pub(crate) selected_download: Arc<Mutex<Option<String>>>,
    pub(crate) checked_downloads: Arc<Mutex<HashSet<String>>>,
    pub(crate) sort_state: Arc<Mutex<SortState>>,
    pub(crate) category_filter: Arc<Mutex<CategoryFilter>>,
}

pub(crate) fn derive_action_availability(
    descriptors: &HomeActionDescriptorState,
    registry: &HomeActionRegistry,
) -> ActionAvailability {
    ActionAvailability {
        can_open_selected: descriptors.find(HomeActionId::Open).map(|descriptor| descriptor.enabled).unwrap_or(false),
        can_open_selected_folder: descriptors.find(HomeActionId::OpenFolder).map(|descriptor| descriptor.enabled).unwrap_or(false),
        can_delete_selected: descriptors.find(HomeActionId::Delete).map(|descriptor| descriptor.enabled).unwrap_or(false),
        can_resume_selected: descriptors.find(HomeActionId::Resume).map(|descriptor| descriptor.enabled).unwrap_or(false),
        can_stop_selected: descriptors.find(HomeActionId::Pause).map(|descriptor| descriptor.enabled).unwrap_or(false),
        can_move_selected_up: registry.action_state.can_move_up,
        can_move_selected_down: registry.action_state.can_move_down,
        can_requeue_selected: descriptors.find(HomeActionId::Requeue).map(|descriptor| descriptor.enabled).unwrap_or(false),
    }
}

pub(crate) fn current_home_action_state_from_snapshot(
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> HomeActionState {
    let state = LAST_QUEUE_SNAPSHOT
        .lock()
        .ok()
        .and_then(|guard| guard.clone())
        .unwrap_or_else(|| load_queue_ui_state(queue_id, sort_state, category_filter));
    let snapshot = sync_selection_to_visible_rows(&state.row_ids, selected_download, checked_downloads);
    let checked_ids = snapshot.selected_ids.iter().cloned().collect::<HashSet<_>>();
    derive_home_action_state(&state.row_ids, &state.rows, &checked_ids, snapshot.main_selected_id.as_ref())
}

pub(crate) fn current_home_action_registry_from_snapshot(
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> RegistryAlias {
    let action_state = current_home_action_state_from_snapshot(
        queue_id,
        selected_download,
        checked_downloads,
        sort_state,
        category_filter,
    );
    derive_home_action_registry(queue_id, action_state, sort_state, category_filter)
}

pub(crate) fn current_downloads_menu_presentation_from_snapshot(
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> DownloadsMenuPresentation {
    let registry = current_home_action_registry_from_snapshot(
        queue_id,
        selected_download,
        checked_downloads,
        sort_state,
        category_filter,
    );
    let descriptors = derive_home_action_descriptors(&registry);
    derive_downloads_menu_presentation(&descriptors)
}

pub(crate) fn build_queue_refresh_derived(
    queue_id: i64,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
    sort_state: &Arc<Mutex<SortState>>,
    category_filter: &Arc<Mutex<CategoryFilter>>,
) -> QueueRefreshDerived {
    let state = load_queue_ui_state(queue_id, sort_state, category_filter);
    let snapshot = sync_selection_to_visible_rows(&state.row_ids, selected_download.clone(), checked_downloads.clone());
    let checked_ids = snapshot.selected_ids.iter().cloned().collect::<HashSet<_>>();
    let state = apply_checked_rows(state, &checked_ids);
    let selected_queue_index = state
        .queue_ids
        .iter()
        .position(|id| *id == queue_id)
        .map(|idx| idx as i32)
        .unwrap_or(0);
    let selected_index = snapshot
        .main_selected_id
        .as_ref()
        .and_then(|id| state.row_ids.iter().position(|row_id| row_id == id))
        .map(|idx| idx as i32)
        .unwrap_or(-1);
    let action_state = derive_home_action_state(&state.row_ids, &state.rows, &checked_ids, snapshot.main_selected_id.as_ref());
    let registry = derive_home_action_registry(queue_id, action_state, sort_state, category_filter);
    let descriptors = derive_home_action_descriptors(&registry);
    let downloads_menu = derive_downloads_menu_presentation(&descriptors);
    let scheduler_state = load_queue_scheduler_state(queue_id);
    QueueRefreshDerived {
        state,
        selected_queue_index,
        selected_index,
        descriptors,
        registry,
        downloads_menu,
        scheduler_state,
    }
}

pub(crate) fn apply_queue_refresh_derived(
    weak: &slint::Weak<MainWindow>,
    derived: QueueRefreshDerived,
) -> DownloadsMenuPresentation {
    let downloads_menu_for_ui = derived.downloads_menu.clone();
    let descriptors = derived.descriptors.clone();
    let registry = derived.registry.clone();
    let scheduler_state = derived.scheduler_state.clone();
    let state = derived.state;
    let selected_queue_index = derived.selected_queue_index;
    let selected_index = derived.selected_index;
    let downloads_menu = derived.downloads_menu;
    let availability = derive_action_availability(&descriptors, &registry);
    let _ = weak.upgrade_in_event_loop(move |app| {
        app.set_selected_queue_index(selected_queue_index);
        app.set_queue_groups(ModelRc::new(VecModel::from(state.queue_labels)));
        app.set_download_rows(ModelRc::new(VecModel::from(state.rows)));
        app.set_queue_config_summary(state.queue_summary.into());
        app.set_queue_name_text(scheduler_state.queue_name.clone().into());
        app.set_selected_download_index(selected_index);
        app.set_can_open_selected(availability.can_open_selected);
        app.set_can_open_selected_folder(availability.can_open_selected_folder);
        app.set_can_delete_selected(availability.can_delete_selected);
        app.set_can_resume_selected(availability.can_resume_selected);
        app.set_can_stop_selected(availability.can_stop_selected);
        app.set_can_move_selected_up(availability.can_move_selected_up);
        app.set_can_move_selected_down(availability.can_move_selected_down);
        app.set_can_requeue_selected(availability.can_requeue_selected);
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
    downloads_menu
}
