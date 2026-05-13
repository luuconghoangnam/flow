use std::collections::HashSet;
use std::sync::{Arc, Mutex};

#[derive(Clone, Debug, Default)]
pub(crate) struct SelectionSnapshot {
    pub(crate) selected_ids: Vec<String>,
    pub(crate) main_selected_id: Option<String>,
}

pub(crate) fn sync_selection_to_visible_rows(
    row_ids: &[String],
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
) -> SelectionSnapshot {
    let visible_ids = row_ids.iter().cloned().collect::<HashSet<_>>();

    let selected_ids = if let Ok(mut checked) = checked_downloads.lock() {
        checked.retain(|id| visible_ids.contains(id));
        checked.iter().cloned().collect::<Vec<_>>()
    } else {
        Vec::new()
    };

    let main_selected_id = if let Ok(mut selected) = selected_download.lock() {
        if selected.as_ref().is_some_and(|id| visible_ids.contains(id)) {
            selected.clone()
        } else {
            *selected = selected_ids.first().cloned();
            selected.clone()
        }
    } else {
        selected_ids.first().cloned()
    };

    SelectionSnapshot {
        selected_ids,
        main_selected_id,
    }
}

pub(crate) fn set_main_selection(
    row_ids: &[String],
    index: i32,
    selected_download: Arc<Mutex<Option<String>>>,
) -> Option<String> {
    let chosen = row_ids.get(index.max(0) as usize).cloned();
    if let Ok(mut selected) = selected_download.lock() {
        *selected = chosen.clone();
    }
    chosen
}

pub(crate) fn toggle_item_selection(
    row_ids: &[String],
    index: i32,
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
) -> Option<(usize, bool, String)> {
    let row_id = row_ids.get(index.max(0) as usize).cloned()?;
    let (checked_count, became_checked) = if let Ok(mut checked) = checked_downloads.lock() {
        let became_checked = checked.insert(row_id.clone());
        if !became_checked {
            checked.remove(&row_id);
        }
        (checked.len(), became_checked)
    } else {
        (0, false)
    };
    if let Ok(mut selected) = selected_download.lock() {
        *selected = Some(row_id.clone());
    }
    Some((checked_count, became_checked, row_id))
}

pub(crate) fn select_all_visible(
    row_ids: &[String],
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
) -> usize {
    if let Ok(mut checked) = checked_downloads.lock() {
        checked.clear();
        checked.extend(row_ids.iter().cloned());
    }
    if let Ok(mut selected) = selected_download.lock() {
        *selected = row_ids.first().cloned();
    }
    row_ids.len()
}

pub(crate) fn clear_selection(
    selected_download: Arc<Mutex<Option<String>>>,
    checked_downloads: Arc<Mutex<HashSet<String>>>,
) {
    if let Ok(mut selected) = selected_download.lock() {
        *selected = None;
    }
    if let Ok(mut checked) = checked_downloads.lock() {
        checked.clear();
    }
}
