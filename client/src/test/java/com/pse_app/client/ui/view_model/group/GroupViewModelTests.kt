@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.group


import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GroupViewModelTests: ViewModelTests<GroupViewModel>() {
    override fun makeViewModel() = GroupViewModel(group.id, facade)
    val group = testingGroup(facade, me, "group")

    @Test
    fun `activeGroup is correct`() = runViewModelTest {
        assertEquals(group.id, viewModel.activeGroup.id)
    }
    @Test
    fun `currentUser sends a default`() = runViewModelTest {
        every { facade.observeActiveUser() } returns flow { emit(null) }
        assertDoesNotThrow {
            assertThat(listOf(null, me)).doesNotContain(viewModel.currentUser.also {
                advanceUntilIdle()
            }.value)
        }
    }
    @Test
    fun `currentUser propagates if available`() = runViewModelTest {
        assertSame(viewModel.currentUser.also {
            advanceUntilIdle()
        }.value, me)
    }
    @Test
    fun `navigation does navigation`() = runViewModelTest {
        viewModel.navigateToTransaction()
        assertAndClearSingleNavigation()
        navigations.clear()

        viewModel.navigateToGroupSettings()
        assertAndClearSingleNavigation()
    }
    @Test
    fun `state makes sense`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        assertEquals(group.transactions.first(), viewModel.uiState.also {
            advanceUntilIdle()
        }.first()?.transactions)
        assertEquals(facade.getCurrency(), viewModel.currency)
    }
    @Test
    fun `state makes sense part 2`() = runViewModelTest {
        every { group.transactions } returns flowOf(null)
        advanceUntilIdle()
        assertEquals(group.transactions.first(), viewModel.uiState.also {
            advanceUntilIdle()
        }.first()?.transactions)
    }
}
