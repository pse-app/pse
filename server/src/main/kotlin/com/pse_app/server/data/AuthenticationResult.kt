package com.pse_app.server.data

/**
 * Models the successful result of an authentication with the PSE server.
 * 
 * @param user The authenticated user.
 * @param refreshToken The newly created refresh token for this user.
 */
data class AuthenticationResult(val user: UserInfo, val refreshToken: String)
