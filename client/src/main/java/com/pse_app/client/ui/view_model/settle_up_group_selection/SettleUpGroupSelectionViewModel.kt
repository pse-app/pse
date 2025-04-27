package com.pse_app.client.ui.view_model.settle_up_group_selection

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.facade.Transaction
import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Currency

/**
 * Standard implementation of [AbstractSettleUpGroupSelectionViewModel]
 */
class SettleUpGroupSelectionViewModel(
    private val model: ModelFacade,
    selectedUserId: String,
) : AbstractSettleUpGroupSelectionViewModel() {
    private val _isDispatching = MutableStateFlow(false)
    override val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    override var currency: Currency? = null
        private set

    private val internalUiState = MutableStateFlow(
        SettleUpGroupSelectionUIState(
            SettleUpGroupSelectionUIState.UserDescription("", null),
            BigDec(0),
            listOf()
        )
    )
    override val uiState = internalUiState.asStateFlow()
    private val selectedGroups: MutableSet<String> = mutableSetOf()

    private lateinit var activeUser: User
    private val otherUser = model.getUserById(selectedUserId)
    private var ready: Boolean = false
    private val settleLock = Mutex()

    override val inputsAreValid: StateFlow<Boolean> =
        uiState.map { state ->
            state.groups.any { groupDescription ->
                groupDescription.selectedAmount != BigDec.ZERO
            }
        }.toStateFlow(false)

    override fun onEntry() {
        super.onEntry()
        launchViewModel {
            currency = model.getCurrency()
        }
        launchViewModel {
            activeUser = model.observeActiveUser().first()!!
            val recommendation = model.getSettleRecommendation(otherUser)
            selectedGroups.addAll(recommendation.amountsByGroup.keys.map { it.id })
            internalUiState.value = SettleUpGroupSelectionUIState(
                SettleUpGroupSelectionUIState.UserDescription(
                    recommendation.user.displayName.first()!!,
                    recommendation.user.profilePicture.first()
                ),
                recommendation.totalAmount,
                recommendation.amountsByGroup.mapNotNull {
                    it.key.displayName.first()?.let { name ->
                        SettleUpGroupSelectionUIState.GroupDescription(
                            it.key.id,
                            name,
                            it.value,
                            it.value
                        )
                    }
                }
            )
            ready = true
        }
    }

    override fun changeAmountForGroup(group: String, newAmount: BigDec) = runCatchingFlow {
        internalUiState.value = internalUiState.value.copy(
            groups = internalUiState.value.groups.map {
                if (it.id == group) {
                    it.copy(
                        selectedAmount = newAmount
                    )
                } else {
                    it
                }
            },
        )
        val total = calculateTotal()
        internalUiState.value = internalUiState.value.copy(
            total = total
        )
    }

    override fun changeGroupSelection(group: String, newSelectionState: Boolean) = runCatchingFlow {
        if (newSelectionState) {
            selectedGroups.add(group)
        } else {
            selectedGroups.remove(group)
        }
        internalUiState.value = internalUiState.value.copy(
            total = calculateTotal()
        )
    }

    private fun calculateTotal(): BigDec {
        return internalUiState.value.groups.fold(BigDec(0)) { acc, e ->
            if (selectedGroups.contains(e.id)) {
                acc + e.selectedAmount
            } else {
                acc + BigDec(0)
            }
        }
    }

    override fun settle(): Boolean {
        if (!ready) return false
        launchViewModel {
            if (settleLock.isLocked) return@launchViewModel
            settleLock.withLock {
                val transactions = mutableMapOf<Group, Transaction>()
                for (groupDesc in internalUiState.value.groups) {
                    if (!selectedGroups.contains(groupDesc.id)) continue
                    if (groupDesc.selectedAmount == BigDec.ZERO) continue

                    val group = model.getGroupById(groupDesc.id)

                    transactions[group] = Transaction.Payment(
                        name = "Settle Up",
                        comment = null,
                        timestamp = Instant.now(),
                        originatingUser = activeUser,
                        balanceChanges = mapOf(
                            activeUser to -groupDesc.selectedAmount,
                            otherUser to groupDesc.selectedAmount
                        )
                    )
                }
                try {
                    _isDispatching.value = true
                    model.postTransactions(transactions)
                } finally {
                    _isDispatching.value = false
                }
            }
            navigate(NavigationEvent.ReturnToMainMenu)
        }
        return true
    }
}
