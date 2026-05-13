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
pub(crate) struct DownloadsMenuActionItem {
    pub(crate) title: String,
    pub(crate) enabled: bool,
    pub(crate) command_id: String,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct DownloadsMenuSubItem {
    pub(crate) title: String,
    pub(crate) target_index: i32,
    pub(crate) command_id: String,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct DownloadsMenuPresentation {
    pub(crate) primary_actions: Vec<DownloadsMenuActionItem>,
    pub(crate) copy_actions: Vec<DownloadsMenuActionItem>,
    pub(crate) move_queue_items: Vec<DownloadsMenuSubItem>,
    pub(crate) move_category_items: Vec<DownloadsMenuSubItem>,
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
                enabled: action_state.can_requeue,
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
    let primary_ids = [
        (HomeActionId::Edit, "edit"),
        (HomeActionId::RestartDownload, "restart-download"),
        (HomeActionId::Properties, "properties"),
        (HomeActionId::FileChecksum, "file-checksum"),
    ];
    let copy_ids = [
        (HomeActionId::CopyLinks, "copy-links"),
        (HomeActionId::CopyAsCurl, "copy-as-curl"),
    ];
    let primary_actions = primary_ids
        .iter()
        .filter_map(|(id, command_id)| {
            let descriptor = descriptors.simple(id.clone())?;
            Some(DownloadsMenuActionItem {
                title: descriptor.title.to_string(),
                enabled: descriptor.enabled,
                command_id: (*command_id).to_string(),
            })
        })
        .collect::<Vec<_>>();
    let copy_actions = copy_ids
        .iter()
        .filter_map(|(id, command_id)| {
            let descriptor = descriptors.simple(id.clone())?;
            Some(DownloadsMenuActionItem {
                title: descriptor.title.to_string(),
                enabled: descriptor.enabled,
                command_id: (*command_id).to_string(),
            })
        })
        .collect::<Vec<_>>();
    let move_queue_items = descriptors
        .submenu(HomeActionId::MoveToQueue)
        .map(|descriptor| {
            descriptor
                .submenu_labels
                .iter()
                .enumerate()
                .map(|(index, label)| DownloadsMenuSubItem {
                    title: format!("{}: {}", descriptor.title, label),
                    target_index: index as i32,
                    command_id: "move-to-queue".to_string(),
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();
    let move_category_items = descriptors
        .submenu(HomeActionId::MoveToCategory)
        .map(|descriptor| {
            descriptor
                .submenu_labels
                .iter()
                .enumerate()
                .map(|(index, label)| DownloadsMenuSubItem {
                    title: format!("{}: {}", descriptor.title, label),
                    target_index: index as i32,
                    command_id: "move-to-category".to_string(),
                })
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    DownloadsMenuPresentation {
        primary_actions,
        copy_actions,
        move_queue_items,
        move_category_items,
    }
}
