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
pub(crate) struct DownloadsMenuPresentation {
    pub(crate) edit_title: String,
    pub(crate) edit_enabled: bool,
    pub(crate) restart_title: String,
    pub(crate) restart_enabled: bool,
    pub(crate) properties_title: String,
    pub(crate) properties_enabled: bool,
    pub(crate) checksum_title: String,
    pub(crate) checksum_enabled: bool,
    pub(crate) copy_links_title: String,
    pub(crate) copy_links_enabled: bool,
    pub(crate) copy_as_curl_title: String,
    pub(crate) copy_as_curl_enabled: bool,
    pub(crate) move_queue_title: String,
    pub(crate) move_category_title: String,
    pub(crate) move_queue_labels: Vec<String>,
    pub(crate) move_category_labels: Vec<String>,
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
    let edit = descriptors.find(HomeActionId::Edit);
    let restart = descriptors.find(HomeActionId::RestartDownload);
    let properties = descriptors.find(HomeActionId::Properties);
    let checksum = descriptors.find(HomeActionId::FileChecksum);
    let copy_links = descriptors.find(HomeActionId::CopyLinks);
    let copy_as_curl = descriptors.find(HomeActionId::CopyAsCurl);
    let move_queue = descriptors.find(HomeActionId::MoveToQueue);
    let move_category = descriptors.find(HomeActionId::MoveToCategory);

    DownloadsMenuPresentation {
        edit_title: edit.map(|it| it.title.to_string()).unwrap_or_else(|| "Edit".to_string()),
        edit_enabled: edit.map(|it| it.enabled).unwrap_or(false),
        restart_title: restart.map(|it| it.title.to_string()).unwrap_or_else(|| "Restart Download".to_string()),
        restart_enabled: restart.map(|it| it.enabled).unwrap_or(false),
        properties_title: properties.map(|it| it.title.to_string()).unwrap_or_else(|| "Properties".to_string()),
        properties_enabled: properties.map(|it| it.enabled).unwrap_or(false),
        checksum_title: checksum.map(|it| it.title.to_string()).unwrap_or_else(|| "File Checksum".to_string()),
        checksum_enabled: checksum.map(|it| it.enabled).unwrap_or(false),
        copy_links_title: copy_links.map(|it| it.title.to_string()).unwrap_or_else(|| "Copy Links".to_string()),
        copy_links_enabled: copy_links.map(|it| it.enabled).unwrap_or(false),
        copy_as_curl_title: copy_as_curl.map(|it| it.title.to_string()).unwrap_or_else(|| "Copy as cURL".to_string()),
        copy_as_curl_enabled: copy_as_curl.map(|it| it.enabled).unwrap_or(false),
        move_queue_title: move_queue.map(|it| it.title.to_string()).unwrap_or_else(|| "Move to Queue".to_string()),
        move_category_title: move_category.map(|it| it.title.to_string()).unwrap_or_else(|| "Move to Category".to_string()),
        move_queue_labels: move_queue.map(|it| it.submenu_labels.clone()).unwrap_or_default(),
        move_category_labels: move_category.map(|it| it.submenu_labels.clone()).unwrap_or_default(),
    }
}
