package com.pse_app.client.model.testing

import com.pse_app.client.model.repositories.UserRepo
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.repositories.local.LocalUserRepo

class MockUserRepo : LocalUserRepo(), UserRepo {
    override suspend fun refreshActiveUser() {
        // Nothing
    }

    override suspend fun refreshUsers(userIds: List<UserId>?) {
        // Nothing
    }

    override suspend fun setDisplayName(name: String) {
        // Nothing
    }
}
