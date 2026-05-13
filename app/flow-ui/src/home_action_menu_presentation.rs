use slint::{ModelRc, VecModel};

use crate::home_action_descriptors::{DownloadsMenuItem, DownloadsMenuPresentation};
use crate::{DownloadsMenuRow, MainWindow};

fn map_item(item: &DownloadsMenuItem) -> DownloadsMenuRow {
    DownloadsMenuRow {
        title: item.title.clone().into(),
        enabled: item.enabled,
        command_id: item.command_id.clone().into(),
        target_index: item.target_index,
        row_kind: item.kind.clone().into(),
        icon: item.icon.clone().into(),
        shortcut: item.shortcut.clone().into(),
    }
}

fn map_top_level_rows(presentation: &DownloadsMenuPresentation) -> Vec<DownloadsMenuRow> {
    presentation.items.iter().map(map_item).collect()
}

pub(crate) fn map_submenu_rows(
    presentation: &DownloadsMenuPresentation,
    parent_index: i32,
) -> Vec<DownloadsMenuRow> {
    presentation
        .items
        .get(parent_index.max(0) as usize)
        .map(|item| item.children.iter().map(map_item).collect::<Vec<_>>())
        .unwrap_or_default()
}

pub(crate) fn apply_downloads_menu_presentation(
    app: &MainWindow,
    presentation: &DownloadsMenuPresentation,
) {
    app.set_downloads_menu_rows(ModelRc::new(VecModel::from(map_top_level_rows(presentation))));
    app.set_downloads_submenu_rows(ModelRc::new(VecModel::from(Vec::<DownloadsMenuRow>::new())));
}
