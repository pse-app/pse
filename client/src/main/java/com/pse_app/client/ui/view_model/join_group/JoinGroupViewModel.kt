package com.pse_app.client.ui.view_model.join_group

import com.pse_app.client.LocalizedStrings
import com.pse_app.client.model.exceptions.InvalidInviteLink
import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.prettyPrintException
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.GroupView
import com.pse_app.client.ui.view.JoinGroup
import com.pse_app.client.ui.view.JoinGroupView
import com.pse_app.client.ui.view_model.SimpleMessageException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * View Model for [JoinGroupView].
 */
class JoinGroupViewModel(
    private val facade: ModelFacade,
    private val inviteToken: String?,
) : AbstractJoinGroupViewModel() {
    private val _state =
        MutableStateFlow<JoinGroupUiState>(JoinGroupUiState.Loading(isLoadingData = true))
    override val state = _state.asStateFlow()

    override fun onEntry() {
        super.onEntry()
        if (inviteToken == null) {
            noInviteToken()
            return
        }
        launchViewModel {
            try {
                val info = facade.getGroupInviteInfo(inviteToken)
                if (info.alreadyMember)
                    onComplete(info.id)
                else
                    _state.value = JoinGroupUiState.Info(info)
            } catch (ex: ModelException) {
                _state.value = JoinGroupUiState.Failed(prettyPrintException(ex))
            }
        }
    }

    override fun accept() {
        if (inviteToken == null) {
            noInviteToken()
            return
        }
        launchViewModel {
            try {
                _state.value = JoinGroupUiState.Loading(isLoadingData = false)
                val group = facade.joinGroup(inviteToken)
                onComplete(group.id)
            } catch (ex: ModelException) {
                _state.value = JoinGroupUiState.Failed(prettyPrintException(ex))
            } catch (ex: InvalidInviteLink) {
                _state.value = JoinGroupUiState.Failed(prettyPrintException(
                    SimpleMessageException(LocalizedStrings.invalid_invite_link, ex))
                )
            }
        }
    }

    override fun decline() = launchViewModel {
        goBack()
    }

    private fun noInviteToken() {
        _state.value = JoinGroupUiState.Failed(LocalizedStrings.invalid_invite_link_format)
    }

    private fun onComplete(groupId: String) = navigate(
        NavigationEvent.CloseThenNav(
            here = JoinGroup(inviteToken),
            destination = GroupView(groupId),
        )
    )
}
