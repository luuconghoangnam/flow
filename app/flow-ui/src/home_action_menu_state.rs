use crate::home_action_state::HomeActionState;
use crate::home_actions::{MoveCategoryTarget, MoveQueueTarget};

#[derive(Clone, Debug, Default)]
pub(crate) struct MoveMenuSection<T> {
    pub(crate) labels: Vec<String>,
    targets: Vec<T>,
}

impl<T: Clone> MoveMenuSection<T> {
    pub(crate) fn resolve(&self, index: i32) -> Option<T> {
        self.targets.get(index.max(0) as usize).cloned()
    }
}

#[derive(Clone, Debug, Default)]
pub(crate) struct HomeActionMenuState {
    pub(crate) move_to_queue: MoveMenuSection<MoveQueueTarget>,
    pub(crate) move_to_category: MoveMenuSection<MoveCategoryTarget>,
}

pub(crate) fn derive_home_action_menu_state(
    action_state: &HomeActionState,
    queue_targets: Vec<MoveQueueTarget>,
    category_targets: Vec<MoveCategoryTarget>,
) -> HomeActionMenuState {
    let move_to_queue_targets = if action_state.selected_ids.is_empty() {
        Vec::new()
    } else {
        queue_targets
    };
    let selected_categories = action_state
        .selected_categories
        .iter()
        .map(|category| category.to_ascii_lowercase())
        .collect::<Vec<_>>();
    let move_to_category_targets = if action_state.selected_ids.is_empty() {
        Vec::new()
    } else {
        category_targets
            .into_iter()
            .filter(|target| {
                !selected_categories.is_empty()
                    && !selected_categories.iter().all(|current| current == &target.label.to_ascii_lowercase())
            })
            .collect::<Vec<_>>()
    };

    HomeActionMenuState {
        move_to_queue: MoveMenuSection {
            labels: move_to_queue_targets.iter().map(|target| target.label.clone()).collect(),
            targets: move_to_queue_targets,
        },
        move_to_category: MoveMenuSection {
            labels: move_to_category_targets.iter().map(|target| target.label.clone()).collect(),
            targets: move_to_category_targets,
        },
    }
}
