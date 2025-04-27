package com.pse_app.client.ui.view_model.main_menu

import com.pse_app.client.model.facade.Group
import com.pse_app.client.ui.view.CreateGroupView
import com.pse_app.client.ui.view.JoinGroupView
import com.pse_app.client.ui.view.ProfileView
import com.pse_app.client.ui.view.SettleUpUserSelectionView
import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Abstract View Model for [MainMenuViewModel].
 */
abstract class AbstractMainMenuViewModel : BaseViewModel() {

    /**
     * [StateFlow] on the UI state.
     */
    abstract val uiState: StateFlow<MainMenuUIState?>

    /**
     * [Currency] to be used.
     */
    abstract val currency: Currency?

    /**
     * Explicitly refresh the listed groups
     * */
    abstract suspend fun refresh()

    /**
     * Select the [group].
     */
    abstract fun selectGroup(group: Group)

    /**
     * Navigates to [CreateGroupView].
     */
    abstract fun navigateToCreateGroup()

    /**
     * Navigates to [ProfileView].
     */
    abstract fun navigateToProfile()

    /**
     * Navigates to [SettleUpUserSelectionView].
     */
    abstract fun navigateToSettleUp()

    /**
     * Opens [JoinGroupView].
     */
    abstract fun joinGroupConfirm(invite: String?)
}
