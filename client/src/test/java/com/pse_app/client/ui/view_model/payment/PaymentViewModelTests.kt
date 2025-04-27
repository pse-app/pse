@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.payment

import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import com.pse_app.common.util.BigDec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PaymentViewModelTests: ViewModelTests<PaymentViewModel>() {
    override fun makeViewModel() = PaymentViewModel(group.id, facade)

    private val group = testingGroup(facade, me, "group")

    @Before
    fun init() {
        coEvery { facade.refreshActiveUser() } just runs
    }

    @Test
    fun `onEntry does something that this test will now test`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        coVerify { facade.getCurrency() }
        coVerify { group.refreshPartial() }
        coVerify { facade.refreshActiveUser() }
        assertEquals(facade.getCurrency(), viewModel.currency)
    }

    @Test
    fun `navigate does navigation`() = runViewModelTest {
        viewModel.navigateToExpense()
        advanceUntilIdle()
        assertAndClearSingleNavigation()
    }

    @Test
    fun `setAmountForUser sets the amount for that user`() = runViewModelTest {
        viewModel.setAmountForUser(me, BigDec("6969"))
        advanceUntilIdle()
        assertEquals(viewModel.amountByUser.value[me], BigDec("6969"))
    }

    @Test
    fun `changeComment changes comment`() = runViewModelTest {
        viewModel.changeComment("comment")
        advanceUntilIdle()
        assertEquals(viewModel.comment.value, "comment")
    }

    @Test
    fun `confirm confirms`() = runViewModelTest {
        coEvery { group.addPayment(any(), any(), any(), any(), any()) } just runs
        viewModel.setAmountForUser(me, BigDec("6969"))
        viewModel.changeComment(" ")
        viewModel.confirm()
        advanceUntilIdle()
        assertAndClearSingleNavigation()
        coVerify { group.addPayment(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `setPayingUser sets the paying user (i know shocking)`() = runViewModelTest {
        val other = group.members.first()!!.first { it != me }
        viewModel.setPayingUser(other)
        advanceUntilIdle()
        assertEquals(other, viewModel.payingUser.value)
    }
}
