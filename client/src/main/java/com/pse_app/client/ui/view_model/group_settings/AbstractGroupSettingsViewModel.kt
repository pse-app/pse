package com.pse_app.client.ui.view_model.group_settings

import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Abstract View Model for [GroupSettingsViewModel].
 */
abstract class AbstractGroupSettingsViewModel : BaseViewModel() {
    /**
     * Whether a group name change request is currently being executed.
     */
    abstract val isDispatchingNameChange: StateFlow<Boolean>

    /**
     * [Flow] on the UI state.
     */
    abstract val uiState: Flow<GroupSettingsUIState>

    /**
     * [Flow] on the group members.
     */
    abstract val groupMembers: Flow<List<GroupUserData>>

    /**
     * [Currency] to be used.
     */
    abstract val currency: Currency?

    /**
     * [StateFlow] on the current display name of the group.
     */
    abstract val currentGroupName: StateFlow<String>

    /**
     * [StateFlow] on the validity of the current group name.
     */
    abstract val currentGroupNameIsValid: StateFlow<Boolean>

    /**
     * Kick [user] from the group.
     */
    abstract fun kickUser(user: User)

    /**
     * Change [currentGroupName].
     */
    abstract fun changeGroupName(newName: String)

    /**
     * Confirm changes to the group name. Returns true iff the change was made effective.
     */
    abstract suspend fun confirmChangeGroupNameRequest(): Boolean

    /**
     * Regenerate the group's invite link.
     */
    abstract fun regenerateInviteLink()

    /**
     * Leave the group.
     */
    abstract fun leaveGroup()
}
