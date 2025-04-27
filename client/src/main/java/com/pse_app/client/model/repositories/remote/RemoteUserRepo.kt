package com.pse_app.client.model.repositories.remote

import com.pse_app.client.annotations.SpecifiedInDesign
import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.repositories.UserRepo
import com.pse_app.client.model.repositories.UserRepoObservables
import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.repositories.local.LocalUserRepo
import com.pse_app.common.dto.ChangeDisplayNameRequestPayload
import com.pse_app.common.dto.UserInfoResponsePayload

/**
 * Repository for user related actions and data.
 * Single source of truth for [UserData].
 *
 * Retrieves data from [RemoteAPI]
 */
class RemoteUserRepo(
    private val remoteAPI: RemoteAPI,
    @SpecifiedInDesign
    private val repo: RemoteRepo,
    private val localUserRepo: LocalUserRepo = LocalUserRepo()
) : UserRepo, UserRepoObservables by localUserRepo {
    private fun uInfo2uData(userInfo: UserInfoResponsePayload): UserData {
        return UserData(
            userInfo.id.cast(),
            userInfo.displayName,
            userInfo.profilePicture?.toString()
        )
    }

    private suspend fun setUserData(userInfo: UserInfoResponsePayload) {
        localUserRepo.setUserData(uInfo2uData(userInfo))
    }

    override suspend fun refreshActiveUser() {
        val userInfo = remoteAPI.getActiveUser().unpack()
        val userId = userInfo.id.cast()
        localUserRepo.setActiveUserId(userId)
        setUserData(userInfo)
    }

    override suspend fun refreshUsers(userIds: List<UserId>?) {
        val userInfos: List<UserInfoResponsePayload> = if (userIds == null) {
            remoteAPI.getKnownUsers().unpack()
        } else {
            remoteAPI.getUsers(userIds.map(UserId::cast).toSet()).unpack().values.toList()
        }
        userInfos.forEach {
            setUserData(it)
        }
    }

    override suspend fun setDisplayName(name: String) {
        val userInfo = remoteAPI.setUserDisplayName(ChangeDisplayNameRequestPayload(name)).unpack()
        setUserData(userInfo)
    }
}
