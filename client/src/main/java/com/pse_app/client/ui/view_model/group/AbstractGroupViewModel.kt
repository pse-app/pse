package com.pse_app.client.ui.view_model.group

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view.GroupSettingsView
import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Abstract View Model for [GroupViewModel].
 */
abstract class AbstractGroupViewModel : BaseViewModel() {
    /**
     * [Flow] on the UI state.
     */
    abstract val uiState: Flow<GroupViewUIState?>

    /**
     * The active group.
     */
    abstract val activeGroup: Group

    /**
     * [StateFlow] on the current user.
     */
    abstract val currentUser: StateFlow<User>

    /**
     * [Currency] to be used.
     */
    abstract val currency: Currency?

    /**
     * Explicitly refresh the transactions in this group.
     * */
    abstract suspend fun refresh()

    /**
     * Navigates to one of the Views for creating a new transaction.
     */
    abstract fun navigateToTransaction()

    /**
     * Navigates to [GroupSettingsView].
     */
    abstract fun navigateToGroupSettings()
}
