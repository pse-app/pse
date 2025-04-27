@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.join_group

import com.pse_app.client.model.exceptions.InternalException
import com.pse_app.client.model.exceptions.InvalidInviteLink
import com.pse_app.client.model.facade.GroupInviteInfo
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test


class JoinGroupViewModelTests: ViewModelTests<JoinGroupViewModel>() {
    private val group = testingGroup(facade, me, "group")
    private val inviteToken = runBlocking { group.inviteUrl.first() }!!

    override fun makeViewModel() = JoinGroupViewModel(
        facade,
        inviteToken,
    )

    @Test
    fun `onLoad gets group information if user is not in the group`() = runViewModelTest {
        val info = GroupInviteInfo(
            alreadyMember = false,
            id = group.id,
            displayName = group.displayName.first()!!,
        )
        coEvery { facade.getGroupInviteInfo(inviteToken) } returns info

        assertThat(viewModel.state.value).isEqualTo(JoinGroupUiState.Loading(true))
        viewModel.onEntry()
        advanceUntilIdle()
        assertThat(viewModel.state.value).isEqualTo(JoinGroupUiState.Info(info))

        assertThat(errors).isEmpty()
        assertThat(navigations).isEmpty()
    }

    @Test
    fun `load fails on model exception`() = runViewModelTest {
        val ex = InternalException(Exception())

        coEvery { facade.getGroupInviteInfo(inviteToken) } throws ex

        viewModel.onEntry()

        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(JoinGroupUiState.Failed::class.java)
        assertThat(errors).isEmpty()
        assertThat(navigations).isEmpty()
    }

    @Test
    fun `accept fails on model exception`() = runViewModelTest {
        coEvery { facade.joinGroup(inviteToken) } throws InternalException(Exception())

        viewModel.accept()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(JoinGroupUiState.Failed::class.java)
    }

    @Test
    fun `accept works when it works`() = runViewModelTest {
        coEvery { facade.joinGroup(inviteToken) } returns group
        viewModel.accept()
        advanceUntilIdle()
        assertAndClearSingleNavigation()
    }

    @Test
    fun `accept handles invalid invites`() = runViewModelTest {
        coEvery { facade.joinGroup(inviteToken) } throws InvalidInviteLink()
        viewModel.accept()
        advanceUntilIdle()
        assertThat(viewModel.state.first()).isInstanceOf(JoinGroupUiState.Failed::class.java)
    }

    @Test
    fun `decine navigates`() = runViewModelTest {
        viewModel.decline()
        advanceUntilIdle()
        assertAndClearSingleNavigation()
    }
}

class JoinGroupViewModelNoTokenTests: ViewModelTests<JoinGroupViewModel>() {
    override fun makeViewModel() = JoinGroupViewModel(facade, null)

    @Test
    fun `load fails without invite token`() = runViewModelTest {
        viewModel.onEntry()

        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(JoinGroupUiState.Failed::class.java)
        assertThat(errors).isEmpty()
        assertThat(navigations).isEmpty()
    }

    @Test
    fun `accept fails without invite token`() = runViewModelTest {
        viewModel.accept()

        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(JoinGroupUiState.Failed::class.java)

        assertThat(errors).isEmpty()
        assertThat(navigations).isEmpty()
    }

}
