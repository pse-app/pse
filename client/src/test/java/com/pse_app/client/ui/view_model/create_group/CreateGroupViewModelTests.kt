@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.create_group

import com.pse_app.client.model.facade.Group
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test


class CreateGroupViewModelTests: ViewModelTests<CreateGroupViewModel>() {
    override fun makeViewModel() = CreateGroupViewModel(facade)

    @Test
    fun `createGroup creates group if name is valid`() = runViewModelTest {
        val group = mockk<Group>()
        coEvery { facade.createGroup("name") } returns group
        coEvery { group.id } returns "lkjasjkfhashfkasjfgwajwabc"
        viewModel.createGroup("name")
        advanceUntilIdle()

        coVerify(exactly = 1) { facade.createGroup("name") }
        assertAndClearSingleNavigation()
    }

    @Test
    fun `createGroup does not create group if name is invalid`() = runViewModelTest {
        viewModel.createGroup("")
        advanceUntilIdle()

        coVerify(exactly = 0) { facade.createGroup(any()) }
    }
}
