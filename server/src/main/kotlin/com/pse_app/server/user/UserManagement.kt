package com.pse_app.server.user

import com.pse_app.common.dto.UserId
import com.pse_app.server.data.Result
import com.pse_app.server.data.UserInfo

/**
 * The user management interface
 */
interface UserManagement {

    /**
     * Retrieves the [UserInfo] associated with the provided user. For inactive users, this returns a stub [UserInfo]. 
     */
    fun getUserInfo(user: UserId): Result<UserInfo>

    /**
     * Sets the display name for an active user. If the user is deactivated or doesn't exist, this returns an [Result.Error].
     */
    fun setDisplayName(user: UserId, displayName: String): Result<Unit>

    /**
     * Deactivates the provided user. If the user is already deactivated, this does nothing. If the user doesn't exist,
     * returns an [Result.Error].
     */
    fun deactivateUser(user: UserId): Result<Unit>
}
