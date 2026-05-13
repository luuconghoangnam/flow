use crate::home_action_state::HomeActionState;

#[derive(Clone, Debug, Default)]
pub(crate) struct SelectionAffordanceState {
    pub(crate) can_open: bool,
    pub(crate) can_open_folder: bool,
    pub(crate) can_delete: bool,
    pub(crate) can_resume: bool,
    pub(crate) can_pause: bool,
    pub(crate) can_move_up: bool,
    pub(crate) can_move_down: bool,
    pub(crate) can_requeue: bool,
}

pub(crate) fn derive_selection_affordance(action_state: &HomeActionState) -> SelectionAffordanceState {
    SelectionAffordanceState {
        can_open: action_state.can_open,
        can_open_folder: action_state.can_open_folder,
        can_delete: action_state.can_delete,
        can_resume: action_state.can_resume,
        can_pause: action_state.can_pause,
        can_move_up: action_state.can_move_up,
        can_move_down: action_state.can_move_down,
        can_requeue: action_state.can_requeue,
    }
}
