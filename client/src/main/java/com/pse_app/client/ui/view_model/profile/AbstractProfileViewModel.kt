package com.pse_app.client.ui.view_model.profile

import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract view model for [ProfileViewModel].
 */
abstract class AbstractProfileViewModel : BaseViewModel() {
    /**
     * Whether a name change request is currently being executed
     */
    abstract val isDispatchingNameChange: StateFlow<Boolean>

    /**
     * Set [displayName] to [newPartialName]
     */
    abstract fun enterDisplayName(newPartialName: String)

    /**
     * Commit new display name. Returns true iff the display name change was made effective.
     */
    abstract suspend fun confirmChangeDisplayNameRequest(): Boolean

    /**
     * Log out of session.
     */
    abstract fun logOut()

    /**
     * Deactivate active account.
     */
    abstract fun deactivateAccount()

    /**
     * [Flow] on active user profile picture URI if available.
     */
    abstract val profilePicture: Flow<String?>

    /**
     * [Flow] on active user display name.
     */
    abstract val displayName: Flow<String?>

    /**
     * [Flow] on the validity of the [displayName].
     */
    abstract val displayNameIsValid: Flow<Boolean>
}
