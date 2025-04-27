package com.pse_app.client.ui.view_model.payment

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view.ExpenseView
import com.pse_app.client.ui.view_model.BaseViewModel
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Abstract View Model for [PaymentViewModel].
 */
abstract class AbstractPaymentViewModel : BaseViewModel() {
    /**
     * Whether a payment request with the server is currently in progress
     */
    abstract val isDispatching: StateFlow<Boolean>

    /**
     * [Group] to create payment in.
     */
    abstract val selectedGroup: Group

    /**
     * [Currency] to use.
     */
    abstract val currency: Currency?

    /**
     * [StateFlow] on the balances of the members of the [selectedGroup].
     */
    abstract val userBalances: Flow<Map<User, BigDec>>

    /**
     * [StateFlow] on the current user.
     */
    abstract val currentUser: StateFlow<User>

    /**
     * [StateFlow] on the validity of the inputs.
     */
    abstract val inputsAreValid: StateFlow<Boolean>

    /**
     * [StateFlow] on the payment amount by user.
     */
    abstract val amountByUser: StateFlow<Map<User, BigDec>>

    /**
     * [StateFlow] on the comment of this payment.
     */
    abstract val comment: StateFlow<String>

    /**
    * [StateFlow] on the user that is going to pay
    * */
    abstract val payingUser: StateFlow<User>

    /**
     * Navigate to [ExpenseView].
     */
    abstract fun navigateToExpense()

    /**
     * Set payment amount for [user] to [amount].
     */
    abstract fun setAmountForUser(user: User, amount: BigDec)

    /**
     * Change payment comment to [newComment].
     */
    abstract fun changeComment(newComment: String)

    /**
     * Set the user that is going to pay to [user].
     */
    abstract fun setPayingUser(user: User)

    /**
     * Commits the payment.
     */
    abstract fun confirm()
}
