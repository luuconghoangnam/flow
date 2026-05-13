use std::collections::HashSet;

use crate::home_action_status::{
    is_editable_status, is_finished_status, is_pausable_status, is_requeueable_status,
    is_resumable_status,
};


#[allow(dead_code)]
#[derive(Clone, Debug, Default)]
pub(crate) struct HomeActionState {
    pub(crate) selected_ids: Vec<String>,
    pub(crate) selected_categories: Vec<String>,
    pub(crate) default_item_index: Option<usize>,
    pub(crate) selection_count: usize,
    pub(crate) resumable_ids: Vec<String>,
    pub(crate) pausable_ids: Vec<String>,
    pub(crate) requeueable_ids: Vec<String>,
    pub(crate) restartable_ids: Vec<String>,
    pub(crate) can_open: bool,
    pub(crate) can_open_folder: bool,
    pub(crate) can_delete: bool,
    pub(crate) can_resume: bool,
    pub(crate) can_pause: bool,
    pub(crate) can_move_up: bool,
    pub(crate) can_move_down: bool,
    pub(crate) can_requeue: bool,
    pub(crate) can_edit: bool,
    pub(crate) can_file_checksum: bool,
    pub(crate) can_restart: bool,
}

pub(crate) fn derive_home_action_state(
    row_ids: &[String],
    rows: &[crate::DownloadRow],
    checked_ids: &HashSet<String>,
    main_selected_id: Option<&String>,
) -> HomeActionState {
    let mut selected_indexes = row_ids
        .iter()
        .enumerate()
        .filter(|(_, id)| checked_ids.contains(*id))
        .map(|(index, _)| index)
        .collect::<Vec<_>>();

    if selected_indexes.is_empty() {
        if let Some(main_id) = main_selected_id {
            if let Some(index) = row_ids.iter().position(|id| id == main_id) {
                selected_indexes.push(index);
            }
        }
    }

    let selected_ids = selected_indexes
        .iter()
        .filter_map(|index| row_ids.get(*index).cloned())
        .collect::<Vec<_>>();
    let selected_categories = selected_indexes
        .iter()
        .filter_map(|index| rows.get(*index).map(|row| row.category.to_string()))
        .collect::<Vec<_>>();

    let default_item_index = main_selected_id
        .and_then(|main_id| {
            selected_indexes
                .iter()
                .copied()
                .find(|index| row_ids.get(*index) == Some(main_id))
        })
        .or_else(|| selected_indexes.first().copied());

    let selection_count = selected_ids.len();

    let resumable_ids = selected_indexes
        .iter()
        .filter_map(|index| {
            let row = rows.get(*index)?;
            if is_resumable_status(row.status.as_str()) {
                row_ids.get(*index).cloned()
            } else {
                None
            }
        })
        .collect::<Vec<_>>();

    let pausable_ids = selected_indexes
        .iter()
        .filter_map(|index| {
            let row = rows.get(*index)?;
            if is_pausable_status(row.status.as_str()) {
                row_ids.get(*index).cloned()
            } else {
                None
            }
        })
        .collect::<Vec<_>>();

    let requeueable_ids = selected_indexes
        .iter()
        .filter_map(|index| {
            let row = rows.get(*index)?;
            if is_requeueable_status(row.status.as_str()) {
                row_ids.get(*index).cloned()
            } else {
                None
            }
        })
        .collect::<Vec<_>>();

    let restartable_ids = selected_indexes
        .iter()
        .filter_map(|index| {
            let row = rows.get(*index)?;
            if !is_pausable_status(row.status.as_str()) {
                row_ids.get(*index).cloned()
            } else {
                None
            }
        })
        .collect::<Vec<_>>();

    let default_row = default_item_index.and_then(|index| rows.get(index));
    let can_open = selection_count > 0
        && selected_indexes.iter().all(|index| {
            rows.get(*index)
                .map(|row| is_finished_status(row.status.as_str()))
                .unwrap_or(false)
        });
    let can_open_folder = selection_count > 0;
    let can_delete = selection_count > 0;
    let can_resume = !resumable_ids.is_empty();
    let can_pause = !pausable_ids.is_empty();
    let can_move_up = selection_count == 1 && default_item_index.map(|index| index > 0).unwrap_or(false);
    let can_move_down = selection_count == 1
        && default_item_index
            .map(|index| index + 1 < rows.len())
            .unwrap_or(false);
    let can_requeue = !requeueable_ids.is_empty();
    let can_edit = selection_count == 1
        && default_row
            .map(|row| is_editable_status(row.status.as_str()))
            .unwrap_or(false);
    let can_file_checksum = selection_count > 0
        && selected_indexes.iter().all(|index| {
            rows.get(*index)
                .map(|row| is_finished_status(row.status.as_str()))
                .unwrap_or(false)
        });
    let can_restart = !restartable_ids.is_empty();

    HomeActionState {
        selected_ids,
        selected_categories,
        default_item_index,
        selection_count,
        resumable_ids,
        pausable_ids,
        requeueable_ids,
        restartable_ids,
        can_open,
        can_open_folder,
        can_delete,
        can_resume,
        can_pause,
        can_move_up,
        can_move_down,
        can_requeue,
        can_edit,
        can_file_checksum,
        can_restart,
    }
}
