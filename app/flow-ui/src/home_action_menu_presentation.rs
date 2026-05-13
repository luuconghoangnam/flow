use slint::{ModelRc, VecModel};

use crate::home_action_descriptors::{DownloadsMenuActionItem, DownloadsMenuPresentation, DownloadsMenuSubItem};
use crate::{DownloadsMenuActionRow, DownloadsMenuSubRow, MainWindow};

fn map_action_rows(items: &[DownloadsMenuActionItem]) -> Vec<DownloadsMenuActionRow> {
    items
        .iter()
        .map(|item| DownloadsMenuActionRow {
            title: item.title.clone().into(),
            enabled: item.enabled,
            command_id: item.command_id.clone().into(),
        })
        .collect::<Vec<_>>()
}

fn map_sub_rows(items: &[DownloadsMenuSubItem]) -> Vec<DownloadsMenuSubRow> {
    items
        .iter()
        .map(|item| DownloadsMenuSubRow {
            title: item.title.clone().into(),
            target_index: item.target_index,
            command_id: item.command_id.clone().into(),
        })
        .collect::<Vec<_>>()
}

pub(crate) fn apply_downloads_menu_presentation(
    app: &MainWindow,
    presentation: &DownloadsMenuPresentation,
) {
    app.set_downloads_menu_primary_actions(ModelRc::new(VecModel::from(map_action_rows(&presentation.primary_actions))));
    app.set_downloads_menu_copy_actions(ModelRc::new(VecModel::from(map_action_rows(&presentation.copy_actions))));
    app.set_downloads_menu_move_queue_items(ModelRc::new(VecModel::from(map_sub_rows(&presentation.move_queue_items))));
    app.set_downloads_menu_move_category_items(ModelRc::new(VecModel::from(map_sub_rows(&presentation.move_category_items))));
}
