package com.pse_app.client.ui.view_model.main_menu

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.CreateGroup
import com.pse_app.client.ui.view.GroupView
import com.pse_app.client.ui.view.JoinGroup
import com.pse_app.client.ui.view.MainMenu
import com.pse_app.client.ui.view.MainMenuView
import com.pse_app.client.ui.view.Profile
import com.pse_app.client.ui.view.SettleUpUserSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.util.Currency

/**
 * View Model for [MainMenuView].
 */
class MainMenuViewModel(
    private val facade: ModelFacade,
) : AbstractMainMenuViewModel() {
    override var currency: Currency? = null
        private set

    private val observingGroups = Mutex()

    override fun onEntry() = runCatchingFlow {
        launchViewModel {
            currency = facade.getCurrency()
        }
        launchViewModel {
            facade.refreshActiveUser()
        }
        launchViewModel {
            // Ensure only one observing job exists
            if (!observingGroups.tryLock()) { return@launchViewModel }
            // Logic to sort groups by mostRecentTransaction
            facade.observeGroups().collect { groups ->
                if (groups == null) {
                    // Nothing to sort
                    _uiState.value = null;return@collect
                }
                // Display groups and then listen for changes to mostRecentTransaction
                displayGroups(groups)
                groups.forEach { group ->
                    // Launch listener
                    launch {
                        var discardedFirst = false
                        group.mostRecentTransaction.collect { _ ->
                            if (discardedFirst) {
                                // Only display after change
                                displayGroups(groups)
                            }
                            discardedFirst = true
                        }
                    }
                }
            }
        }
    }

    // Displays groups in correct order
    private suspend fun displayGroups(groups: List<Group>) {
        // Query mostRecentTransaction only once to avoid rare edge cases where it
        // changes mid sort
        val sortableGroups = groups.map { Pair(it, it.mostRecentTransaction.first()) }
            .toMutableList()
        sortableGroups.sortByDescending { it.second ?: Instant.EPOCH }
        _uiState.value = MainMenuUIState(sortableGroups.map { it.first })
    }

    override suspend fun refresh() {
        facade.refreshActiveUser()
    }

    private val _uiState = MutableStateFlow<MainMenuUIState?>(null)
    override val uiState: StateFlow<MainMenuUIState?> = _uiState.asStateFlow()

    override fun selectGroup(group: Group) =
        navigate(NavigationEvent.SimpleNav(GroupView(group.id)))

    override fun navigateToCreateGroup() = navigate(NavigationEvent.SimpleNav(CreateGroup))

    override fun navigateToProfile() = navigate(NavigationEvent.SimpleNav(Profile))

    override fun navigateToSettleUp() = navigate(NavigationEvent.SimpleNav(SettleUpUserSelection))

    override fun joinGroupConfirm(invite: String?) = navigate(
        NavigationEvent.ViaPop(
            via = MainMenu,
            destination = JoinGroup(invite),
        )
    )

    override fun goBack() = navigate(NavigationEvent.CloseApp)
}
