package com.pse_app.client.model.data_layer

import com.pse_app.client.persistence.Preferences

/** Preferences key for access token */
const val ACCESS_TOKEN_KEY = "SESSION.ACCESS_TOKEN"
/** Preferences key for refresh token */
const val REFRESH_TOKEN_KEY = "SESSION.REFRESH_TOKEN"

/**
 * A persistent [SessionStore] backed by [Preferences]
 */
class SessionPreferenceStore(private val preferences: Preferences) : SessionStore {
    // Since preferences doesn't support storing null, null is stored as empty strings

    override suspend fun getSession(): Session? {
        val accessToken = preferences.get(ACCESS_TOKEN_KEY)
        val refreshToken = preferences.get(REFRESH_TOKEN_KEY)
        return if (accessToken == null || refreshToken == null
            || accessToken == "" || refreshToken == "") {
            null
        } else {
            Session(accessToken, refreshToken)
        }
    }

    override suspend fun setSession(session: Session?) {
        preferences.set(ACCESS_TOKEN_KEY, session?.accessToken ?: "")
        preferences.set(REFRESH_TOKEN_KEY, session?.refreshToken ?: "")
    }
}
