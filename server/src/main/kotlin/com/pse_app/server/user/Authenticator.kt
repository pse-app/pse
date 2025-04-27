package com.pse_app.server.user

import com.pse_app.common.dto.UserId
import com.pse_app.server.data.AuthenticationResult
import com.pse_app.server.data.Result
import com.pse_app.server.data.UserInfo
import java.net.URI

/**
 * The authenticator interface.
 */
interface Authenticator {

    /**
     * Takes user information from an OpenID ID-Token and creates a new session. If a user with the provided
     * id does not yet exist, a new user is created using the provided information. If a user already exists,
     * [displayName] is ignored, and [profilePicture] replaces the current profile picture URI if present.
     * 
     * Then, a new session is created for the user and [createSession] returns an [AuthenticationResult] for
     * the newly created session containing a fresh refresh token.
     */
    fun createSession(user: UserId, displayName: String, profilePicture: URI?): Result<AuthenticationResult>

    /**
     * Takes a refresh token and looks it up. If it is valid, replaces it with a new refresh token and returns
     * an [AuthenticationResult] containing the associated [UserInfo] as well as the new refresh token.
     */
    fun refreshSession(refreshToken: String): Result<AuthenticationResult>

    /**
     * Takes a [UserId]. Returns a [Result.Success] if and only if the user exists and is active.
     */
    fun authenticate(user: UserId): Result<Unit>
}
