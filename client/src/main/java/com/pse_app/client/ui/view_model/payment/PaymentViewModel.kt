package com.pse_app.client.ui.view_model.payment

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.DummyUser
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.ExpenseView
import com.pse_app.client.ui.view.GroupView
import com.pse_app.client.ui.view.PaymentView
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Currency

/**
 * Standard implementation of [AbstractPaymentViewModel]
 */
class PaymentViewModel(
    private val activeGroupId: String,
    private val model: ModelFacade,
) : AbstractPaymentViewModel() {
    private val _isDispatching = MutableStateFlow(false)
    override val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    override val selectedGroup: Group = model.getGroupById(activeGroupId)

    override var currency: Currency? = null
        private set

    override val userBalances: Flow<Map<User, BigDec>> = selectedGroup.balances

    override val currentUser: StateFlow<User> =
        model.observeActiveUser().filterNotNull().toStateFlow(DummyUser)

    private val inputValidities: MutableStateFlow<Map<User, Boolean>> = MutableStateFlow(mapOf())

    private val _amountsSentToUsers: MutableStateFlow<Map<User, BigDec>> = MutableStateFlow(mapOf())
    override val amountByUser: StateFlow<Map<User, BigDec>> = _amountsSentToUsers

    override val inputsAreValid: StateFlow<Boolean> = inputValidities.map { validities ->
        validities.values.all { isValid -> isValid }
    }.combine(amountByUser) { isValidSoFar, amountSentToUsers ->
        isValidSoFar && amountSentToUsers.values.any { amount -> amount != BigDec.ZERO }
    }.toStateFlow(false)


    private val _comment: MutableStateFlow<String> = MutableStateFlow("")
    override val comment: StateFlow<String> = _comment.asStateFlow()

    private val _payingUser: MutableStateFlow<User> = MutableStateFlow(DummyUser)
    override val payingUser: StateFlow<User>
        get() = _payingUser

    override fun onEntry() {
        super.onEntry()

        inputValidities.value = mapOf()

        launchViewModel {
            currency = model.getCurrency()
        }
        launchViewModel {
            selectedGroup.refreshPartial()
        }
        launchViewModel {
            model.refreshActiveUser()
        }
        launchViewModel {
            val fetchedCurrentUser = currentUser.filter { user -> user != DummyUser }.first()
            // We only want to override the paying user if the user hasn't already selected someone.
            // Crucially, we need to check this condition *after* getting the first real currentUser
            // since that might take a while during which the user might select someone
            if (payingUser.value == DummyUser) {
                _payingUser.value = fetchedCurrentUser
            }
        }
    }

    override fun navigateToExpense() = navigate(
        NavigationEvent.CloseThenNav(
            destination = ExpenseView(activeGroupId),
            here = PaymentView(activeGroupId),
        )
    )

    override fun setAmountForUser(
        user: User,
        amount: BigDec
    ) = runCatchingFlow {
        _amountsSentToUsers.value += mapOf(user to amount)
    }

    override fun changeComment(newComment: String) = runCatchingFlow {
        _comment.value = newComment
    }

    override fun setPayingUser(user: User) = runCatchingFlow {
        _payingUser.value = user
    }

    override fun confirm() = launchViewModel {
        val payingUser = payingUser.value
        val balanceChanges = amountByUser.value.toMutableMap()
        balanceChanges.remove(payingUser)

        try {
            _isDispatching.value = true
            selectedGroup.addPayment(
                "",
                comment.value.ifBlank { null },
                balanceChanges,
                payingUser,
                currentUser.value
            )
        } finally {
            _isDispatching.value = false
        }

        navigate(NavigationEvent.BackSkipNav(GroupView(activeGroupId)))
    }
}
