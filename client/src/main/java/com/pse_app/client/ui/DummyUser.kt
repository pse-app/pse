package com.pse_app.client.ui

import com.pse_app.client.model.facade.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Dummy used to initially stub out a user before the real one arrives over a flow. */
object DummyUser: User {
    override val id: String
        get() = ""
    override val displayName: Flow<String?>
        get() = flowOf()
    override val profilePicture: Flow<String?>
        get() = flowOf()
}

