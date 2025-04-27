package com.pse_app.client.ui.view_model.group_settings

import com.pse_app.client.LocalizedStrings
import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.GroupSettingsView
import com.pse_app.client.ui.view_model.SimpleMessageException
import com.pse_app.client.ui.view_model.validateDisplayName
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip
import java.util.Currency

/**
 * View Model for the [GroupSettingsView].
 */
class GroupSettingsViewModel(
    activeGroupId: String,
    private val model: ModelFacade,
) : AbstractGroupSettingsViewModel() {
    private val _isDispatchingNameChange = MutableStateFlow(false)
    override val isDispatchingNameChange: StateFlow<Boolean> =
        _isDispatchingNameChange.asStateFlow()

    private val activeGroup: Group by lazy {
        model.getGroupById(activeGroupId)
    }
    private var activeUser: User? = null

    private val uncommitedGroupName = MutableStateFlow<String?>(null)

    override val currentGroupName: StateFlow<String> by lazy {
        activeGroup.displayName.filterNotNull().combine(uncommitedGroupName) { model, local ->
            local ?: model
        }.toStateFlow("")
    }

    override val currentGroupNameIsValid: StateFlow<Boolean> by lazy {
        currentGroupName.map { groupName ->
            validateDisplayName(groupName)
        }.toStateFlow(validateDisplayName(currentGroupName.value))
    }

    override val uiState: Flow<GroupSettingsUIState> by lazy {
        activeGroup.inviteUrl.zip(activeGroup.displayName) { inviteUrl, displayName ->
            GroupSettingsUIState(inviteLink = inviteUrl ?: "", groupName = displayName ?: "")
        }
    }

    override var currency: Currency? = null
        private set

    override fun onEntry() {
        super.onEntry()
        launchViewModel {
            currency = model.getCurrency()
        }
        launchViewModel {
            activeUser = model.observeActiveUser().first()
            if (activeUser == null) {
                navigateHome()
            }
            activeGroup.refreshPartial()
        }
    }

    override val groupMembers: Flow<List<GroupUserData>> by lazy {
        activeGroup.members.zip(activeGroup.balances) { members, balance ->
            val activeUserBalance = balance.getOrDefault(activeUser, BigDec.ZERO)
            members?.map { member ->
                GroupUserData(
                    user = member,
                    balance = balance.getOrDefault(member, BigDec.ZERO),
                    member.id == activeUser?.id,
                    balance.getOrDefault(member, BigDec.ZERO) * activeUserBalance < BigDec(0)
                )
            } ?: listOf()
        }
    }


    override fun kickUser(user: User) = launchViewModel {
        activeGroup.kickUser(user)
        if (user == activeUser) {
            navigateHome()
        }
    }

    override fun changeGroupName(newName: String) {
        uncommitedGroupName.value = newName
    }

    override suspend fun confirmChangeGroupNameRequest(): Boolean {
        val name = uncommitedGroupName.value ?: return true

        runCatchingFlow {
            if (!validateDisplayName(name)) {
                handleException(SimpleMessageException(LocalizedStrings.name_invalid))
                return false
            }

            try {
                _isDispatchingNameChange.value = true
                activeGroup.setDisplayName(name)
            } finally {
                _isDispatchingNameChange.value = false
            }
            uncommitedGroupName.value = null
        }
        return true
    }

    override fun regenerateInviteLink() = launchViewModel {
        activeGroup.regenerateInviteLink()
    }

    override fun leaveGroup() = launchViewModel {
        val user = model.observeActiveUser().first()

        if (user != null) {
            activeGroup.kickUser(user)
            navigateHome()
        }
    }

    private fun navigateHome() = navigate(NavigationEvent.ReturnToMainMenu)
}
