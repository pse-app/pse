package com.pse_app.client.ui.view_model.settle_up_user_selection

import com.pse_app.client.ui.view.MainMenuView
import com.pse_app.client.ui.view.SettleUpUserSelectionView
import com.pse_app.client.ui.view_model.BaseViewModel
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Represents UI state of [SettleUpUserSelectionView].
 */
data class SettleUpUserUIState(
    /**
     * Total owed by or to active user across all groups.
     */
    val total: BigDec,

    /**
     * List of users to settle with.
     */
    val rows: List<SettleUserRow>
)

/**
 * Represents information about potential settlement with one user.
 */
data class SettleUserRow(
    /**
     * User id
     */
    val id: String,

    /**
     * User display name
     */
    val displayName: String,

    /**
     * User profile picture
     */
    val profilePicture: String?,

    /**
     * Proposed settlement amount
     */
    val amount: BigDec
)

/**
 * Abstract View Model for [SettleUpUserSelectionViewModel].
 */
abstract class AbstractSettleUpUserSelectionViewModel : BaseViewModel() {
    /**
     * [StateFlow] on UI state.
     */
    abstract val uiState: StateFlow<SettleUpUserUIState?>

    /**
     * [Currency] to use.
     */
    abstract val currency: Currency?

    /**
     * Navigates to [MainMenuView].
     */
    abstract fun navigateToMainMenu()

    /**
     * Select user to settle with.
     */
    abstract fun selectUser(userId: String)
}
