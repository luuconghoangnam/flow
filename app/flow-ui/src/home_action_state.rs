use std::collections::HashSet;

#[allow(dead_code)]
#[derive(Clone, Debug, Default)]
pub(crate) struct HomeActionState {
    pub(crate) selected_ids: Vec<String>,
    pub(crate) selected_categories: Vec<String>,
    pub(crate) default_item_index: Option<usize>,
    pub(crate) resumable_ids: Vec<String>,
    pub(crate) pausable_ids: Vec<String>,
    pub(crate) requeueable_ids: Vec<String>,
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
        .and_then(|main_id| selected_indexes.iter().copied().find(|index| row_ids.get(*index) == Some(main_id)))
        .or_else(|| selected_indexes.first().copied());

    let resumable_ids = selected_indexes
        .iter()
        .filter_map(|index| {
            let row = rows.get(*index)?;
            let status = row.status.to_ascii_lowercase();
            if status.contains("paused") || status.contains("failed") || status.contains("stopped") {
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
            let status = row.status.to_ascii_lowercase();
            if status.contains("downloading") || status.contains("queued") {
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
            let status = row.status.to_ascii_lowercase();
            if status.contains("paused")
                || status.contains("failed")
                || status.contains("finished")
                || status.contains("queued")
            {
                row_ids.get(*index).cloned()
            } else {
                None
            }
        })
        .collect::<Vec<_>>();

    let can_open = default_item_index
        .and_then(|index| rows.get(index))
        .map(|row| row.status.to_ascii_lowercase().contains("finished"))
        .unwrap_or(false);
    let can_open_folder = !selected_ids.is_empty();
    let can_delete = !selected_ids.is_empty();
    let can_resume = !resumable_ids.is_empty();
    let can_pause = !pausable_ids.is_empty();
    let can_move_up = default_item_index.map(|index| index > 0).unwrap_or(false);
    let can_move_down = default_item_index
        .map(|index| index + 1 < rows.len())
        .unwrap_or(false);
    let can_requeue = !requeueable_ids.is_empty();
    let can_edit = default_item_index
        .and_then(|index| rows.get(index))
        .map(|row| {
            let status = row.status.to_ascii_lowercase();
            !(status.contains("downloading") || status.contains("queued"))
        })
        .unwrap_or(false);
    let can_file_checksum = selected_indexes.iter().any(|index| {
        rows.get(*index)
            .map(|row| row.status.to_ascii_lowercase().contains("finished"))
            .unwrap_or(false)
    });

    HomeActionState {
        selected_ids,
        selected_categories,
        default_item_index,
        resumable_ids,
        pausable_ids,
        requeueable_ids,
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
    }
}
