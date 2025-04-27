package com.pse_app.client.ui.view_model.expense

import com.pse_app.client.LocalizedStrings
import com.pse_app.client.StringResource
import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.facade.User
import com.pse_app.client.model.facade.stripZeroBalance
import com.pse_app.client.ui.DummyUser
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.ExpenseView
import com.pse_app.client.ui.view.GroupView
import com.pse_app.client.ui.view.PaymentView
import com.pse_app.client.ui.view_model.SimpleMessageException
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.Currency

// The maximum number of decimal digits in any ISO 4217 currency (for fallback)
private const val ISO_4217_MAX_DIGITS = 4

/**
 * View Model for [ExpenseView].
 */
class ExpenseViewModel(
    private val activeGroupId: String,
    private val model: ModelFacade,
) : AbstractExpenseViewModel() {
    private val _isDispatching = MutableStateFlow(false)
    override val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    override val selectedGroup: Group = model.getGroupById(activeGroupId)

    override var currency: Currency? = null
        private set

    override val currentUser: StateFlow<User> =
        model.observeActiveUser().filterNotNull().toStateFlow(DummyUser)

    private val _recordedBalances: MutableStateFlow<Map<User, BigDec>> = MutableStateFlow(mapOf())
    override val recordedBalances: StateFlow<Map<User, BigDec>> = _recordedBalances.asStateFlow()

    private val _total = MutableStateFlow(BigDec.ZERO)
    override val total = _total.asStateFlow()

    private val _inSplitEvenlyMode = MutableStateFlow(false)
    override val inSplitEvenlyMode: StateFlow<Boolean> = _inSplitEvenlyMode

    private val deselectedUsersForSplit = MutableStateFlow(setOf<User>())
    override val selectedUsersForSplit: StateFlow<Set<User>> = selectedGroup.members
        .filterNotNull()
        .combine(deselectedUsersForSplit) { members, deselected ->
            members.toSet() - deselected
        }.toStateFlow(setOf())

    private val numberInputValidities: MutableStateFlow<Map<User, Boolean>> =
        MutableStateFlow(mapOf())
    override val inputsAreValid: StateFlow<Boolean> = numberInputValidities.map { validities ->
        validities.values.all { isValid -> isValid }
    }.combine(recordedBalances) { isValidSoFar, recordedBalances ->
        isValidSoFar && recordedBalances.values.any { balanceChange -> balanceChange != BigDec.ZERO }
    }.toStateFlow(false)


    override fun onEntry() {
        super.onEntry()
        launchViewModel {
            currency = model.getCurrency()
        }
        launchViewModel {
            model.refreshActiveUser()
            selectedGroup.refreshPartial()
        }
    }

    override val users: Flow<List<User>?>
        get() = selectedGroup.members

    override fun navigateToPayment() = navigate(
        NavigationEvent.CloseThenNav(
            destination = PaymentView(activeGroupId),
            here = ExpenseView(activeGroupId),
        )
    )

    override fun setNumberInputValidity(user: User, isValid: Boolean) {
        numberInputValidities.value += mapOf(user to isValid)
    }

    override fun changeAmountForUser(
        user: User,
        newAmount: BigDec
    ) {
        _recordedBalances.value += mapOf(user to newAmount)
        _total.value = _recordedBalances.value.values.fold(BigDec.ZERO) { x, y -> x + y }
    }

    override fun setSplitEvenlyMode(inSplitEvenlyMode: Boolean) = runCatchingFlow {
        _inSplitEvenlyMode.value = inSplitEvenlyMode
        splitEvenlyBasedOnTotal(newTotal = total.value)
    }

    override fun changeUserSelection(user: User, newSelectionState: Boolean) = runCatchingFlow {
        if (newSelectionState) {
            deselectedUsersForSplit.value -= setOf(user)
        } else {
            deselectedUsersForSplit.value += setOf(user)
        }
        splitEvenlyBasedOnTotal(total.value)
    }

    override fun splitEvenlyBasedOnTotal(newTotal: BigDec) = runCatchingFlow {
        val scale = currency?.defaultFractionDigits?.let {
            if (it == -1) ISO_4217_MAX_DIGITS else it
        } ?: ISO_4217_MAX_DIGITS

        _total.value = BigDec(newTotal.value.setScale(scale))

        val selectedUsers = selectedUsersForSplit.value
        if (selectedUsers.isNotEmpty()) {
            _recordedBalances.value = model.splitEvenly(
                _total.value,
                selectedUsers.toList()
            )
        } else {
            _recordedBalances.value = mapOf()
        }
    }


    override fun confirm(expenseName: String) {
        launchViewModel {
            if (isValidExpenseName(expenseName)) {
                val currentUser = currentUser.value
                val split = recordedBalances.value
                // Check active user isn't only one paying
                if ((split[currentUser] ?: BigDec.ZERO) != BigDec.ZERO
                    && stripZeroBalance(split).size == 1) {
                    throw SimpleMessageException(StringResource(LocalizedStrings.expense_only_participant))
                }
                try {
                    _isDispatching.value = true
                    selectedGroup.addExpense(
                        name = expenseName,
                        comment = null,
                        originatingUser = currentUser,
                        split = split,
                        total = total.value
                    )
                } finally {
                    _isDispatching.value = false
                }

                navigate(
                    NavigationEvent.BackSkipNav(
                        GroupView(activeGroupId)
                    )
                )
            }
        }
    }

    override fun isValidExpenseName(name: String): Boolean {
        return name.isNotBlank()
    }
}
