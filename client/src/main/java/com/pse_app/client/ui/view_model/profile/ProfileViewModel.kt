package com.pse_app.client.ui.view_model.profile

import android.util.Log
import com.pse_app.client.LocalizedStrings
import com.pse_app.client.loggingTag
import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view_model.SimpleMessageException
import com.pse_app.client.ui.view_model.validateDisplayName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Standard implementation of [AbstractProfileViewModel].
 */
class ProfileViewModel(
    private val model: ModelFacade,
) : AbstractProfileViewModel() {
    private val _isDispatchingNameChange = MutableStateFlow(false)
    override val isDispatchingNameChange = _isDispatchingNameChange.asStateFlow()

    private val changedDisplayName = MutableStateFlow<String?>(null)

    override fun onEntry() {
        super.onEntry()
        launchViewModel {
            model.refreshActiveUser()
        }
    }

    override fun enterDisplayName(newPartialName: String) = runCatchingFlow {
        Log.d(loggingTag, "enterDisplayName($newPartialName)")
        changedDisplayName.value = newPartialName
    }

    override suspend fun confirmChangeDisplayNameRequest(): Boolean {
        val name = changedDisplayName.value ?: return true

        if (!validateDisplayName(name)) {
            handleException(SimpleMessageException(LocalizedStrings.name_invalid))
            return false
        }

        return try {
            _isDispatchingNameChange.value = true
            model.setUserDisplayName(name)
            changedDisplayName.value = null
            true
        } catch (ex: ModelException) {
            handleException(ex)
            false
        } finally {
            _isDispatchingNameChange.value = false
        }
    }

    override fun logOut() = launchViewModel {
        model.logout()
        resetNav()
    }

    override fun deactivateAccount() = launchViewModel {
        model.deactivateActiveUser()
        resetNav()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val profilePicture: Flow<String?> =
        model.observeActiveUser().filterNotNull().flatMapLatest { it.profilePicture }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val displayName: Flow<String?> = model
        .observeActiveUser().filterNotNull().flatMapLatest { it.displayName }
        .combine(changedDisplayName) { remote, local -> local ?: remote }

    override val displayNameIsValid: Flow<Boolean> = displayName.map { displayName ->
        displayName != null && validateDisplayName(displayName)
    }

    private fun resetNav() = navigate(NavigationEvent.ResetNav)
}
