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

impl HomeActionDescriptorState {
    pub(crate) fn find(&self, id: HomeActionId) -> Option<&HomeActionDescriptor> {
        self.descriptors.iter().find(|descriptor| descriptor.id == id)
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
                enabled: action_state.default_item_index.is_some(),
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
                enabled: action_state.can_open,
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
