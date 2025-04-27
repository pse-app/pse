@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.main_menu

import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class MainMenuViewModelTests: ViewModelTests<MainMenuViewModel>() {
    override fun makeViewModel() = MainMenuViewModel(facade)

    private val groups = listOf(
        testingGroup(facade, me, "A"),
        testingGroup(facade, me, "B"),
    )

    @Before
    fun init() {
        every { facade.observeGroups() } returns flowOf(groups)
    }

    @Test
    fun `onEntry loads stuff`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()

        assertEquals(setOf(groups), setOf(viewModel.uiState.value!!.groups))
        assertEquals(facade.getCurrency(), viewModel.currency)
    }

    @Test
    fun `navigation methods navigate`() = runViewModelTest {
        listOf(
            { viewModel.selectGroup(groups[0]) },
            { viewModel.joinGroupConfirm("token") },
            viewModel::navigateToCreateGroup,
            viewModel::navigateToProfile,
            viewModel::navigateToSettleUp,
            viewModel::goBack,
        ).forEach { call ->
            call()
            assertAndClearSingleNavigation()
        }
    }

    @Test
    fun `refresh refreshes`() = runViewModelTest {
        viewModel.refresh()
        coVerify { facade.refreshActiveUser() }
    }
}
