package com.pse_app.client.model.data_layer

/**
 * Contains the tokens associated with a session.
 */
data class Session(
    /** Access token for protected endpoints */
    val accessToken: String,
    /** Refresh token for refreshing access */
    val refreshToken: String
)
