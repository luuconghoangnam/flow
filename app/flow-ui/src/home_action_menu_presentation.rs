use slint::{ModelRc, VecModel};

use crate::home_action_descriptors::{DownloadsMenuGroupKind, DownloadsMenuPresentation};
use crate::{DownloadsMenuRow, MainWindow};

fn map_rows(presentation: &DownloadsMenuPresentation) -> Vec<DownloadsMenuRow> {
    let mut rows = Vec::new();
    for group in &presentation.groups {
        match group.kind {
            DownloadsMenuGroupKind::Action => {
                rows.extend(group.actions.iter().map(|item| DownloadsMenuRow {
                    title: item.title.clone().into(),
                    enabled: item.enabled,
                    command_id: item.command_id.clone().into(),
                    target_index: -1,
                    row_kind: "action".into(),
                }));
            }
            DownloadsMenuGroupKind::Separator => rows.push(DownloadsMenuRow {
                title: "".into(),
                enabled: false,
                command_id: "".into(),
                target_index: -1,
                row_kind: "separator".into(),
            }),
            DownloadsMenuGroupKind::SubMenu => {
                rows.extend(group.sub_items.iter().map(|item| DownloadsMenuRow {
                    title: item.title.clone().into(),
                    enabled: true,
                    command_id: item.command_id.clone().into(),
                    target_index: item.target_index,
                    row_kind: "submenu".into(),
                }));
            }
        }
    }
    rows
}

pub(crate) fn apply_downloads_menu_presentation(
    app: &MainWindow,
    presentation: &DownloadsMenuPresentation,
) {
    app.set_downloads_menu_rows(ModelRc::new(VecModel::from(map_rows(presentation))));
}
