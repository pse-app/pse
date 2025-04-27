package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import kotlinx.coroutines.flow.Flow

/**
 * UserRepo actions independent of backend.
 */
interface UserRepoObservables {
    /**
     * Get a Flow on the id of the active user.
     * Flow will emit a first value and then values when the data changes.
     * Emits null if no active user exists.
     */
    fun observeActiveUserId(): Flow<UserId?>

    /**
     * Get a Flow on the data of one user.
     * Flow will emit a first value and then values when the data changes.
     * Emits null if user not found.
     */
    fun observeUser(userId: UserId): Flow<UserData?>

    /**
     * Inits [users] with id as display name if they don't exist
     */
    suspend fun touchUsers(users: Set<UserId>)

    /**
     * Delete locally saved state. Use for logout.
     */
    suspend fun clear()
}
