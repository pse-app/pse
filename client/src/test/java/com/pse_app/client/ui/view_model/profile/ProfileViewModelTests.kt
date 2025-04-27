@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.profile

import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileViewModelTests: ViewModelTests<ProfileViewModel>() {
    override fun makeViewModel() = ProfileViewModel(facade)

    @Test
    fun `onEntry does refresh`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        coVerify { facade.refreshActiveUser() }
        assertEquals(viewModel.profilePicture.first(), null)
    }

    @Test
    fun `enterDisplayName is boring`() = runViewModelTest {
        viewModel.enterDisplayName("name")
        advanceUntilIdle()
        assertEquals(viewModel.displayName.first(), "name")
    }

    @Test
    fun `confirmChangeDisplayNameRequest is way too long of a name`() = runViewModelTest {
        viewModel.enterDisplayName("name")
        advanceUntilIdle()
        assertTrue(viewModel.displayNameIsValid.first())
        coEvery { facade.setUserDisplayName("name") } just runs
        assertTrue(viewModel.confirmChangeDisplayNameRequest())
        coVerify { facade.setUserDisplayName("name") }
    }

    @Test
    fun `confirming name change fail invalid name`() = runViewModelTest {
        viewModel.enterDisplayName("")
        advanceUntilIdle()
        assertFalse(viewModel.displayNameIsValid.first())
        assertFalse(viewModel.confirmChangeDisplayNameRequest())
        coVerify(exactly = 0) { facade.setUserDisplayName(any()) }
        assertAndClearErrors()
    }

    @Test
    fun `logout is simple`() = runViewModelTest {
        coEvery { facade.logout() } just runs
        viewModel.logOut()
        advanceUntilIdle()
        assertAndClearSingleNavigation()
        coVerify { facade.logout() }
    }

    @Test
    fun `deactivateAccount is just spicy logout`() =  runViewModelTest {
        coEvery { facade.deactivateActiveUser() } just runs
        viewModel.deactivateAccount()
        advanceUntilIdle()
        assertAndClearSingleNavigation()
        coVerify { facade.deactivateActiveUser() }
    }
}
