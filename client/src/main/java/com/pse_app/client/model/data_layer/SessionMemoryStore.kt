package com.pse_app.client.model.data_layer

/**
 * A simple in-memory [SessionStore]
 */
class SessionMemoryStore : SessionStore {
    private var session: Session? = null

    override suspend fun getSession(): Session? {
        return session
    }

    override suspend fun setSession(session: Session?) {
        this.session = session
    }
}
