package com.pse_app.client.ui.view_model.join_group

import com.pse_app.client.StringId
import com.pse_app.client.StringResource
import com.pse_app.client.model.facade.GroupInviteInfo
import com.pse_app.client.ui.view.JoinGroupView
import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract View Model for [JoinGroupViewModel].
 */
abstract class AbstractJoinGroupViewModel: BaseViewModel() {
    /**
     * [StateFlow] on the UI state.
     */
    abstract val state: StateFlow<JoinGroupUiState>

    /**
     * Accept the invite.
     */
    abstract fun accept()

    /**
     * Decline the invite.
     */
    abstract fun decline()
}

/**
 * Represents the UI state of [JoinGroupView].
 */
sealed interface JoinGroupUiState {
    /**
     * The loading state.
     */
    data class Loading(val isLoadingData: Boolean): JoinGroupUiState

    /**
     * The failed state.
     * @param message The error message to display.
     */
    data class Failed(val message: StringResource): JoinGroupUiState {
        constructor(messageId: StringId) : this(StringResource(messageId))
    }

    /**
     * The state in which info is shown.
     * @param info The [GroupInviteInfo] to display.
     */
    data class Info(val info: GroupInviteInfo): JoinGroupUiState
}
