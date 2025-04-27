package com.pse_app.client.ui.view_model.expense

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view.PaymentView
import com.pse_app.client.ui.view_model.BaseViewModel
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

/**
 * Abstract View Model for [ExpenseViewModel].
 */
abstract class AbstractExpenseViewModel : BaseViewModel() {
    /**
     * [Group] to create expense in.
     */
    abstract val selectedGroup: Group

    /**
     * [Flow] on the users affected by the expense.
     */
    abstract val users: Flow<List<User>?>

    /**
     * [StateFlow] on the current user.
     */
    abstract val currentUser: StateFlow<User>

    /**
     * [StateFlow] on the validity of the inputs.
     */
    abstract val inputsAreValid: StateFlow<Boolean>

    /**
     * [Currency] to be used.
     */
    abstract val currency: Currency?

    /**
     * [Flow] on the total of the expense.
     */
    abstract val total: Flow<BigDec>

    /**
     * [StateFlow] on whether the expense should be split equally.
     */
    abstract val inSplitEvenlyMode: StateFlow<Boolean>

    /**
     * [StateFlow] on the selected users participating in the split.
     */
    abstract val selectedUsersForSplit: StateFlow<Set<User>>

    /**
     * Whether a payment request is currently being sent to the server
     */
    abstract val isDispatching: StateFlow<Boolean>

    /**
     * [StateFlow] on the balance changes of this expense.
     * Unlike the final balance changes sent to the server, this does *not* include the changes to
     * the current user
     */
    abstract val recordedBalances: StateFlow<Map<User, BigDec>>

    /**
     * Navigate to [PaymentView].
     */
    abstract fun navigateToPayment()

    /**
     * Set [inputsAreValid].
     */
    abstract fun setNumberInputValidity(user: User, isValid: Boolean)

    /**
     * Change balance for [user] to [newAmount].
     */
    abstract fun changeAmountForUser(user: User, newAmount: BigDec)

    /**
     * Set [inSplitEvenlyMode].
     */
    abstract fun setSplitEvenlyMode(inSplitEvenlyMode: Boolean)

    /**
     * Select [user]s selection state in [selectedUsersForSplit].
     */
    abstract fun changeUserSelection(user: User, newSelectionState: Boolean)

    /**
     * Change the balances to an equal split with a total of [newTotal].
     */
    abstract fun splitEvenlyBasedOnTotal(newTotal: BigDec)

    /**
     * Checks if the given name [name] is a valid choice for the name of an expense.
     */
    abstract fun isValidExpenseName(name: String): Boolean

    /**
     * Commit this expense with the given name [expenseName].
     */
    abstract fun confirm(expenseName: String)
}
