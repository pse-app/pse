package com.pse_app.client.model.facade.stubs

import com.pse_app.client.model.facade.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PreviewUser(override val id: String, name: String, picture: String?) : User {
    override val displayName: Flow<String?> = flowOf(name)
    override val profilePicture: Flow<String?> = flowOf(picture)
}
