package com.pse_app.client.model.data_layer

/**
 * Interface to persist session information.
 */
interface SessionStore {
    /** Retrieve session or null if it doesn't exist */
    suspend fun getSession(): Session?
    /** Set current session to [session] or null to delete it */
    suspend fun setSession(session: Session?)
}
