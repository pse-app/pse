@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.group_settings

import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import com.pse_app.common.util.BigDec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupSettingsViewModelTests: ViewModelTests<GroupSettingsViewModel>() {
    private val group = testingGroup(facade, me, "group")

    override fun makeViewModel() = GroupSettingsViewModel(group.id, facade)

    private suspend fun TestScope.enterViewModel() {
        viewModel.onEntry()
        advanceUntilIdle()
        coVerify { facade.getCurrency() }
        coVerify { group.refreshPartial() }
        assertEquals(facade.getCurrency(), viewModel.currency)
        assertNoErrors()
        assertNoNavigations()
    }

    @Test
    fun `kickUser kicks other user`() = runViewModelTest {
        enterViewModel()
        val user = mockk<User>()
        coEvery { group.kickUser(user) } just runs
        viewModel.kickUser(user)
        advanceUntilIdle()
        coVerify(exactly = 1) { group.kickUser(user) }
    }

    @Test
    fun `kickUser leaves if self`() = runViewModelTest {
        enterViewModel()
        coEvery { group.kickUser(me) } just runs
        viewModel.kickUser(me)
        advanceUntilIdle()
        coVerify(exactly = 1) { group.kickUser(me) }
        assertAndClearSingleNavigation()
    }

    @Test
    fun `leaveGroup leaves`() = runViewModelTest {
        enterViewModel()
        coEvery { group.kickUser(me) } just runs
        viewModel.leaveGroup()
        advanceUntilIdle()
        coVerify(exactly = 1) { group.kickUser(me) }
        assertAndClearSingleNavigation()
    }

    @Test
    fun `change group name (success)`() = runViewModelTest {
        enterViewModel()
        val name = "name"
        coEvery { group.setDisplayName(name) } answers {
            assertTrue(viewModel.isDispatchingNameChange.value)
        }
        viewModel.changeGroupName(name)
        assertEquals(name, viewModel.currentGroupName.also {
            advanceUntilIdle()
        }.value)
        assertTrue(viewModel.currentGroupNameIsValid.also {
            advanceUntilIdle()
        }.value)
        viewModel.confirmChangeGroupNameRequest()
        advanceUntilIdle()
        coVerify(exactly = 1) { group.setDisplayName(name) }
    }

    @Test
    fun `change group name (failure)`() = runViewModelTest {
        enterViewModel()
        val name = ""
        viewModel.changeGroupName(name)
        advanceUntilIdle()
        assertEquals(name, viewModel.currentGroupName.also {
            advanceUntilIdle()
        }.value)
        assertFalse(viewModel.currentGroupNameIsValid.also {
            advanceUntilIdle()
        }.value)
        viewModel.confirmChangeGroupNameRequest()
        advanceUntilIdle()
        assertAndClearErrors()
    }

    @Test
    fun `regenerateInvite regenerates invites`() = runViewModelTest {
        enterViewModel()
        viewModel.regenerateInviteLink()
    }

    @Test
    fun `groupMembers make sense`() = runViewModelTest {
        enterViewModel()
        val members = group.members.first()!!
        val balances = group.balances.first()
        assertThat(viewModel.groupMembers.also {
            advanceUntilIdle()
        }.first()).allSatisfy { userData ->
            assertThat(members).filteredOn {
                it == userData.user
            }.singleElement().satisfies({ user ->
                assertEquals(balances[user] ?: BigDec(0), userData.balance)
                if (userData.isActiveUser) assertEquals(me, userData.user)
            })
        }
    }

    @Test
    fun `ui state makes sense`() = runViewModelTest {
        enterViewModel()
        val name = group.displayName.first()!!
        val url = group.inviteUrl.first()!!
        val uiState = viewModel.uiState.first()
        assertEquals(url, uiState.inviteLink)
        assertEquals(name, uiState.groupName)
    }
}
