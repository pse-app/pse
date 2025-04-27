package com.pse_app.client.ui.view_model.group

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.DummyUser
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.ExpenseView
import com.pse_app.client.ui.view.GroupSettings
import com.pse_app.client.ui.view.GroupView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Currency

/**
 * View Model for [GroupView].
 */
class GroupViewModel(
    private val activeGroupId: String,
    private val model: ModelFacade,
) : AbstractGroupViewModel() {
    override val activeGroup: Group by lazy {
        model.getGroupById(activeGroupId)
    }

    override val uiState: Flow<GroupViewUIState?> by lazy {
        activeGroup.transactions.map { transactions -> transactions?.let(::GroupViewUIState) }
    }

    override val currentUser: StateFlow<User> by lazy {
        model.observeActiveUser().filterNotNull().toStateFlow(DummyUser)
    }

    override var currency: Currency? = null
        private set

    override fun onEntry() {
        super.onEntry()
        launchViewModel {
            currency = model.getCurrency()
        }
        launchViewModel {
            refresh()
        }
    }

    override suspend fun refresh() {
        viewModelScope.launch { activeGroup.refresh() }.join()
    }

    override fun navigateToTransaction() = navigate(
        NavigationEvent.SimpleNav(
            ExpenseView(activeGroupId)
        )
    )

    override fun navigateToGroupSettings() = navigate(
        NavigationEvent.SimpleNav(
            GroupSettings(activeGroupId)
        )
    )
}
