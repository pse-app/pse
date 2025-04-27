package com.pse_app.client.ui.view_model.settle_up_group_selection

import com.pse_app.client.ui.view.SettleUpGroupSelectionView
import com.pse_app.client.ui.view_model.BaseViewModel
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Represents UI state of [SettleUpGroupSelectionView]
 */
data class SettleUpGroupSelectionUIState (
    /**
     * The user to settle with.
     */
    val userDesc: UserDescription,

    /**
     * Total of selected settlement amounts.
     */
    val total: BigDec,

    /**
     * List of groups to settle in.
     */
    val groups: List<GroupDescription>
) {
    /**
     * Represents the user to settle with
     */
    data class UserDescription(
        /**
         * Display name of the user.
         */
        val displayName: String,

        /**
         * Optional profile picture URI of the user.
         */
        val profilePic: String?
    )

    /**
     * Represents a group to settle in.
     */
    data class GroupDescription (
        /**
         * Group id.
         */
        val id: String,

        /**
         * Display name of the group.
         */
        val displayName: String,

        /**
         * Proposed settlement amount
         */
        val proposedAmount: BigDec,

        /**
         * Selected settlement amount.
         */
        val selectedAmount: BigDec
    )
}

/**
 * Abstract View Model for [SettleUpGroupSelectionViewModel].
 */
abstract class AbstractSettleUpGroupSelectionViewModel : BaseViewModel() {
    /**
     * Whether a settle request is currently being dispatched
     */
    abstract val isDispatching: StateFlow<Boolean>

    /**
     * [StateFlow] on UI state.
     */
    abstract val uiState: StateFlow<SettleUpGroupSelectionUIState>

    /**
     * [StateFlow] on the validity of the inputs.
     */
    abstract val inputsAreValid: StateFlow<Boolean>

    /**
     * [Currency] to use.
     */
    abstract val currency: Currency?

    /**
     * Change selected settlement amount in [group] to [newAmount].
     */
    abstract fun changeAmountForGroup(group: String, newAmount: BigDec)

    /**
     * Change whether to include [group] in current settlement.
     */
    abstract fun changeGroupSelection(group: String, newSelectionState: Boolean)

    /**
     * Perform settlement. Returns true iff the settlement was made effective.
     */
    abstract fun settle(): Boolean
}
