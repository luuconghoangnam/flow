use crate::home_action_registry::HomeActionRegistry;

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum HomeActionKind {
    Simple,
    SubMenu,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum HomeActionId {
    Open,
    OpenFolder,
    Delete,
    Resume,
    Pause,
    Requeue,
    RestartDownload,
    Edit,
    CopyLinks,
    CopyAsCurl,
    Properties,
    FileChecksum,
    MoveToQueue,
    MoveToCategory,
}

#[derive(Clone, Debug)]
pub(crate) struct HomeActionDescriptor {
    pub(crate) id: HomeActionId,
    pub(crate) title: &'static str,
    pub(crate) enabled: bool,
    pub(crate) kind: HomeActionKind,
    pub(crate) submenu_labels: Vec<String>,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct HomeActionDescriptorState {
    pub(crate) descriptors: Vec<HomeActionDescriptor>,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct DownloadsMenuItem {
    pub(crate) kind: String,
    pub(crate) title: String,
    pub(crate) enabled: bool,
    pub(crate) icon: String,
    pub(crate) shortcut: String,
    pub(crate) command_id: String,
    pub(crate) target_index: i32,
    pub(crate) children: Vec<DownloadsMenuItem>,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct DownloadsMenuPresentation {
    pub(crate) items: Vec<DownloadsMenuItem>,
}

impl HomeActionDescriptorState {
    pub(crate) fn find(&self, id: HomeActionId) -> Option<&HomeActionDescriptor> {
        self.descriptors.iter().find(|descriptor| descriptor.id == id)
    }

    pub(crate) fn simple(&self, id: HomeActionId) -> Option<&HomeActionDescriptor> {
        self.find(id)
            .filter(|descriptor| descriptor.kind == HomeActionKind::Simple)
    }

    pub(crate) fn submenu(&self, id: HomeActionId) -> Option<&HomeActionDescriptor> {
        self.find(id)
            .filter(|descriptor| descriptor.kind == HomeActionKind::SubMenu)
    }
}

pub(crate) fn derive_home_action_descriptors(
    registry: &HomeActionRegistry,
) -> HomeActionDescriptorState {
    let action_state = &registry.action_state;
    let menu_state = &registry.menu_state;

    HomeActionDescriptorState {
        descriptors: vec![
            HomeActionDescriptor {
                id: HomeActionId::Open,
                title: "Open",
                enabled: action_state.can_open,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::OpenFolder,
                title: "Open Folder",
                enabled: action_state.can_open_folder,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::Delete,
                title: "Delete",
                enabled: action_state.can_delete,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::Resume,
                title: "Resume",
                enabled: action_state.can_resume,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::Pause,
                title: "Pause",
                enabled: action_state.can_pause,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::Requeue,
                title: "Requeue",
                enabled: action_state.can_requeue,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::RestartDownload,
                title: "Restart Download",
                enabled: action_state.can_restart,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::Edit,
                title: "Edit",
                enabled: action_state.can_edit,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::CopyLinks,
                title: "Copy Links",
                enabled: !action_state.selected_ids.is_empty(),
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::CopyAsCurl,
                title: "Copy as cURL",
                enabled: !action_state.selected_ids.is_empty(),
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::Properties,
                title: "Properties",
                enabled: action_state.default_item_index.is_some(),
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::FileChecksum,
                title: "File Checksum",
                enabled: action_state.can_file_checksum,
                kind: HomeActionKind::Simple,
                submenu_labels: Vec::new(),
            },
            HomeActionDescriptor {
                id: HomeActionId::MoveToQueue,
                title: "Move to Queue",
                enabled: !menu_state.move_to_queue.labels.is_empty(),
                kind: HomeActionKind::SubMenu,
                submenu_labels: menu_state.move_to_queue.labels.clone(),
            },
            HomeActionDescriptor {
                id: HomeActionId::MoveToCategory,
                title: "Move to Category",
                enabled: !menu_state.move_to_category.labels.is_empty(),
                kind: HomeActionKind::SubMenu,
                submenu_labels: menu_state.move_to_category.labels.clone(),
            },
        ],
    }
}

pub(crate) fn derive_downloads_menu_presentation(
    descriptors: &HomeActionDescriptorState,
) -> DownloadsMenuPresentation {
    let action_item = |id: HomeActionId, command_id: &str, icon: &str, shortcut: &str| {
        descriptors.simple(id).map(|descriptor| DownloadsMenuItem {
            kind: "action".to_string(),
            title: descriptor.title.to_string(),
            enabled: descriptor.enabled,
            icon: icon.to_string(),
            shortcut: shortcut.to_string(),
            command_id: command_id.to_string(),
            target_index: -1,
            children: Vec::new(),
        })
    };
    let submenu_child_items = |id: HomeActionId, command_id: &str| {
        descriptors
            .submenu(id)
            .map(|descriptor| {
                descriptor
                    .submenu_labels
                    .iter()
                    .enumerate()
                    .map(|(index, label)| DownloadsMenuItem {
                        kind: "action".to_string(),
                        title: label.clone(),
                        enabled: true,
                        icon: String::new(),
                        shortcut: String::new(),
                        command_id: command_id.to_string(),
                        target_index: index as i32,
                        children: Vec::new(),
                    })
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default()
    };

    let mut items = vec![
        action_item(HomeActionId::Edit, "edit", "edit", ""),
        action_item(HomeActionId::RestartDownload, "restart-download", "refresh", ""),
        action_item(HomeActionId::Properties, "properties", "info", ""),
        action_item(HomeActionId::FileChecksum, "file-checksum", "info", ""),
    ]
    .into_iter()
    .flatten()
    .collect::<Vec<_>>();

    items.push(DownloadsMenuItem {
        kind: "separator".to_string(),
        ..DownloadsMenuItem::default()
    });

    items.extend(
        [
            action_item(HomeActionId::CopyLinks, "copy-links", "copy", ""),
            action_item(HomeActionId::CopyAsCurl, "copy-as-curl", "copy", ""),
        ]
        .into_iter()
        .flatten(),
    );

    let move_queue_children = submenu_child_items(HomeActionId::MoveToQueue, "move-to-queue");
    let move_category_children = submenu_child_items(HomeActionId::MoveToCategory, "move-to-category");
    if !move_queue_children.is_empty() || !move_category_children.is_empty() {
        items.push(DownloadsMenuItem {
            kind: "separator".to_string(),
            ..DownloadsMenuItem::default()
        });
    }
    if let Some(descriptor) = descriptors.submenu(HomeActionId::MoveToQueue) {
        if !move_queue_children.is_empty() {
            items.push(DownloadsMenuItem {
                kind: "submenu".to_string(),
                title: descriptor.title.to_string(),
                enabled: descriptor.enabled,
                icon: "queue".to_string(),
                shortcut: String::new(),
                command_id: String::new(),
                target_index: -1,
                children: move_queue_children,
            });
        }
    }
    if let Some(descriptor) = descriptors.submenu(HomeActionId::MoveToCategory) {
        if !move_category_children.is_empty() {
            items.push(DownloadsMenuItem {
                kind: "submenu".to_string(),
                title: descriptor.title.to_string(),
                enabled: descriptor.enabled,
                icon: "category".to_string(),
                shortcut: String::new(),
                command_id: String::new(),
                target_index: -1,
                children: move_category_children,
            });
        }
    }

    DownloadsMenuPresentation { items }
}
