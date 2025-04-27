@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.expense

import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import com.pse_app.common.util.BigDec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpenseViewModelTests: ViewModelTests<ExpenseViewModel>() {
    override fun makeViewModel() = ExpenseViewModel(group.id, facade)

    private val group = testingGroup(facade, me, "A")

    @Before
    fun initialize() {
        every { facade.splitEvenly(any(), any())} answers {
            val amount = firstArg<BigDec>()
            val users = secondArg<List<User>>()
            users.associateWith { amount }
        }
    }

    @Test
    fun `onEntry refreshes data`() = runViewModelTest {
        assertEquals(group, viewModel.selectedGroup)

        viewModel.onEntry()
        advanceUntilIdle()

        assertThat(viewModel.users.first()).isEqualTo(group.members.first())
        coVerify(exactly = 1) { facade.getCurrency() }
        coVerify(exactly = 1) { facade.refreshActiveUser() }
        coVerify(exactly = 1) { group.refreshPartial() }
    }

    @Test
    fun `navigation does its thing`() = runViewModelTest {
        assertEquals(group, viewModel.selectedGroup)

        viewModel.navigateToPayment()

        assertAndClearSingleNavigation()
    }

    @Test
    fun `change amounts`() = runViewModelTest {
        assertEquals(group, viewModel.selectedGroup)

        val amountMe = BigDec("111")
        viewModel.changeAmountForUser(me, amountMe)
        val other = group.members.first()!!.first { it != me }
        val amountOther = BigDec("222")
        viewModel.changeAmountForUser(other, BigDec("420"))
        viewModel.changeAmountForUser(other, amountOther)

        assertThat(viewModel.recordedBalances.value.toList()).contains(
            me to amountMe,
            other to amountOther,
        )
        assertThat(viewModel.total.value).isEqualTo(BigDec("333"))
    }

    @Test
    fun `setSplitEvenlyMode calls splitEvenly`() = runViewModelTest {
        val amountMe = BigDec("111")
        val amountOther = BigDec("222")
        val members = group.members.first()!!
        val other = members.first { it != me }
        viewModel.changeAmountForUser(me, amountMe)
        viewModel.changeAmountForUser(other, amountOther)
        advanceUntilIdle()
        viewModel.setSplitEvenlyMode(true)
        viewModel.setSplitEvenlyMode(false)
        advanceUntilIdle()

        verify { facade.splitEvenly(BigDec("333"), any()) }

        viewModel.setSplitEvenlyMode(true)
        viewModel.splitEvenlyBasedOnTotal(BigDec("69420"))
        advanceUntilIdle()
        verify { facade.splitEvenly(BigDec("69420"), any()) }
    }

    @Test
    fun `setNumberInputValidity does what it says`() = runViewModelTest {
        viewModel.changeAmountForUser(me, BigDec("3"))
        viewModel.setNumberInputValidity(me, false)
        advanceUntilIdle()
        assertFalse(viewModel.inputsAreValid.value)
        viewModel.setNumberInputValidity(me, true)
        advanceUntilIdle()
        assertTrue(viewModel.inputsAreValid.value)
    }

    @Test
    fun `changeUserSelection does something which a test exists`() = runViewModelTest {
        viewModel.setSplitEvenlyMode(true)
        advanceUntilIdle()
        assertContains(viewModel.selectedUsersForSplit.value, me)
        viewModel.changeUserSelection(me, false)
        advanceUntilIdle()
        assertThat(viewModel.selectedUsersForSplit.value).doesNotContain(me)
        viewModel.changeUserSelection(me, true)
        advanceUntilIdle()
        assertContains(viewModel.selectedUsersForSplit.value, me)
    }

    @Test
    fun `valid expense names`() = runViewModelTest {
        assertTrue(viewModel.isValidExpenseName("Normal Name"))
        assertFalse(viewModel.isValidExpenseName(""))
    }

    @Test
    fun `splitEvenlyBasedOnTotal has this one weird branch I need to cover`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        viewModel.setSplitEvenlyMode(true)
        for (member in viewModel.selectedUsersForSplit.value)
            viewModel.changeUserSelection(member, false)

        advanceUntilIdle()
        assertEquals(setOf(), viewModel.selectedUsersForSplit.value)
        clearRecorededCalls(facade)

        viewModel.splitEvenlyBasedOnTotal(BigDec("6969"))

        advanceUntilIdle()
        verify(exactly = 0) { facade.splitEvenly(any(), any()) }
    }

    @Test
    fun `confirm does its thing`() = runViewModelTest {
        coEvery { group.addExpense(any(), any(), any(), any(), any()) } just runs
        viewModel.changeAmountForUser(me, BigDec("6969"))
        viewModel.setSplitEvenlyMode(true)
        viewModel.confirm("asjKSjfhakjfhawkjhakjfhsahfakwjwhkajfhskhfakfhwajkhskfhakfa")

        advanceUntilIdle()
        assertAndClearSingleNavigation()
        coVerify { group.addExpense(any(), any(), any(), any(), any()) }
    }
}
