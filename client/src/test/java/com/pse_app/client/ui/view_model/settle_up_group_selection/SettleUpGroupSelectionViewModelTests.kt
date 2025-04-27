@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.settle_up_group_selection

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.SettleRecommendation
import com.pse_app.client.model.facade.Transaction
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import com.pse_app.common.util.BigDec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettleUpGroupSelectionViewModelTests: ViewModelTests<SettleUpGroupSelectionViewModel>() {
    override fun makeViewModel() = SettleUpGroupSelectionViewModel(facade, otherUser.id)

    private val amount = BigDec(6969)
    private val group = testingGroup(facade, me, "group")
    private val otherUser = group.members.run { runBlocking { first() } }!!.first { it != me }
    init {
        every { group.transactions } returns flowOf(listOf(
            mockk {
                every { balanceChanges } returns mapOf(
                    me to amount,
                    otherUser to -amount,
                )
            }
        ))
        coEvery { facade.getSettleRecommendation(otherUser) } returns SettleRecommendation(
            user = otherUser,
            amountsByGroup = mapOf(group to amount),
            totalAmount = amount,
        )
    }

    @Test
    fun `enter viewmodel`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        coVerify { facade.getCurrency() }
        coVerify { facade.observeActiveUser() }
        assertEquals(facade.getCurrency(), viewModel.currency)
    }

    @Test
    fun `selections work`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        viewModel.changeGroupSelection(group.id, false)
        advanceUntilIdle()
        assertEquals(viewModel.uiState.value.total, BigDec(0))

        viewModel.changeGroupSelection(group.id, true)
        advanceUntilIdle()
        assertTrue(viewModel.inputsAreValid.value)
        assertThat(viewModel.uiState.value.groups).satisfiesExactly({
            assertEquals(group.id, it.id)
            assertEquals(amount, it.proposedAmount)
            assertEquals(amount, it.selectedAmount)
        })
        assertEquals(amount, viewModel.uiState.value.total)
    }

    @Test
    fun `changing amount works`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        val newAmount = BigDec(420)
        viewModel.changeGroupSelection(group.id, true)
        viewModel.changeAmountForGroup(group.id, newAmount)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.groups).satisfiesExactly({
            assertEquals(group.id, it.id)
            assertEquals(amount, it.proposedAmount)
            assertEquals(newAmount, it.selectedAmount)
        })
    }

    @Test
    fun `settling works`() = runViewModelTest {
        coEvery { facade.postTransactions(any()) } answers {
            val transactions = firstArg<Map<Group, Transaction>>().entries
            assertThat(transactions).singleElement().satisfies({ (theGroup, transaction) ->
                assertEquals(group, theGroup)
                assertEquals(mapOf(me to -amount, otherUser to amount), transaction.balanceChanges)
                assertEquals(me, transaction.originatingUser)
            })
        }
        viewModel.onEntry()
        advanceUntilIdle()
        viewModel.changeGroupSelection(group.id, true)
        viewModel.settle()
        advanceUntilIdle()

        coVerify(exactly = 1) { facade.postTransactions(any()) }
        assertAndClearSingleNavigation()
    }
}
