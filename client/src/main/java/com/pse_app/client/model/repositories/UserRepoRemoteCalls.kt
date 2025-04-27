package com.pse_app.client.model.repositories

import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.exceptions.NoActiveUserException
import com.pse_app.client.model.repositories.data.UserId

/**
 * UserRepo actions assuming existence of backend.
 */
interface UserRepoRemoteCalls {
    /**
     * Refresh data of active user.
     *
     * @throws ModelException
     */
    suspend fun refreshActiveUser()

    /**
     * Refresh Data for provided users or all known users if null is provided.
     *
     * @throws ModelException
     */
    suspend fun refreshUsers(userIds: List<UserId>? = null)

    /**
     * Change display name of active user to provided value.
     *
     * @throws ModelException
     * @throws NoActiveUserException
     */
    suspend fun setDisplayName(name: String)
}
