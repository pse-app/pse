package com.pse_app.client.model.testing

import com.pse_app.client.model.repositories.GroupRepo
import com.pse_app.client.model.repositories.UserRepoObservables
import com.pse_app.client.model.repositories.data.AllRecommendationData
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.repositories.local.LocalGroupRepo
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.first

class MockGroupRepo(
    private val userRepo: UserRepoObservables
): LocalGroupRepo(), GroupRepo {
    override suspend fun refreshAllGroups() {
        throw NotImplementedError()
    }

    override suspend fun refreshGroup(groupId: GroupId, fetchBalances: Boolean) {
        throw NotImplementedError()
    }

    override suspend fun createGroup(name: String): GroupId {
        val gid = GroupId("RANDOM")
        this.setGroupData(
            GroupData(
                gid,
                name,
                "",
                listOf(userRepo.observeActiveUserId().first()!!),
                mapOf(userRepo.observeActiveUserId().first()!! to BigDec(0)),
                null
            )
        )
        return gid
    }

    override suspend fun joinGroup(inviteToken: String): GroupId {
        throw NotImplementedError()
    }

    override suspend fun regenerateInviteLink(groupId: GroupId) {
        throw NotImplementedError()
    }

    override suspend fun kickUser(groupId: GroupId, userId: UserId) {
        throw NotImplementedError()
    }

    override suspend fun setDisplayName(groupId: GroupId, name: String) {
        throw NotImplementedError()
    }

    override suspend fun getAllSettleRecommendations(): AllRecommendationData {
        throw NotImplementedError()
    }
}
