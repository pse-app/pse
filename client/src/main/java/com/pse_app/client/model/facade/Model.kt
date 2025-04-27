package com.pse_app.client.model.facade

import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.exceptions.BadResponseException
import com.pse_app.client.model.exceptions.InvalidInviteLink
import com.pse_app.client.model.repositories.Repo
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.persistence.Preferences
import com.pse_app.common.dto.SettingsResponsePayload
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URI
import java.util.Currency

/**
 * Implements [ModelFacade] by abstracting interactions with [Repo], [RemoteAPI] and [Preferences]
 */
class Model(
    private val repo: Repo,
    private val remoteAPI: RemoteAPI,
    @Suppress("UnusedPrivateProperty") // Specified in app architecture
    private val preferences: Preferences
) : ModelFacade {
    private var serverSettings: SettingsResponsePayload? = null

    init {
        remoteAPI.registerOnSessionDestroyed {
            repo.clear()
        }
    }

    override suspend fun isAuthenticated(): Boolean = catchInternal {
        return remoteAPI.refreshSession()
    }

    override suspend fun login(idToken: String) = catchInternal {
        remoteAPI.createSession(idToken)
    }

    override suspend fun logout() = catchInternal {
        remoteAPI.logout()
    }

    override suspend fun setUserDisplayName(name: String) = catchInternal {
        repo.userRepo.setDisplayName(name)
    }

    override suspend fun deactivateActiveUser() = catchInternal {
        remoteAPI.deactivateActiveUser()
        remoteAPI.logout()
    }

    override fun observeActiveUser(): Flow<User?> = catchInternal {
        return repo.userRepo.observeActiveUserId()
            .map{ it?.let{ RepoUser(repo, it) } }
    }

    override suspend fun refreshActiveUser() = catchInternal {
        repo.userRepo.refreshActiveUser()
        // Only refresh groups once active user is fresh
        repo.groupRepo.refreshAllGroups()
    }

    override suspend fun refreshUsers(users: List<User>?) = catchInternal {
        repo.userRepo.refreshUsers(users?.let { it.map{ user -> UserId(user.id) } })
    }

    override fun observeGroups(): Flow<List<Group>?> = catchInternal {
        return repo.groupRepo.observeAllGroupIds().map {
            it?.map { groupId -> RepoGroup(repo, groupId) }
        }
    }

    override fun getGroupById(id: String): Group = catchInternal {
        return RepoGroup(repo, GroupId(id))
    }

    override fun getUserById(id: String): User = catchInternal {
        return RepoUser(repo, UserId(id))
    }

    override suspend fun createGroup(name: String): Group = catchInternal {
        val groupId = repo.groupRepo.createGroup(name)
        return RepoGroup(repo, groupId)
    }

    override suspend fun joinGroup(inviteToken: String): Group = catchInternal {
        val groupId = repo.groupRepo.joinGroup(inviteToken)
        return RepoGroup(repo, groupId)
    }

    override suspend fun getGroupInviteInfo(inviteToken: String): GroupInviteInfo = catchInternal {
        val response = remoteAPI.getGroupInviteInfo(inviteToken)
        val info = when (response.status) {
            HttpStatusCode.BadRequest ->
                throw InvalidInviteLink()
            else -> response.unpack()
        }
        GroupInviteInfo(
            displayName = info.displayName,
            alreadyMember = info.alreadyMember,
            id = info.id.id.toString()
        )
    }

    private fun settleRecommendationConvert(data: SettleRecommendationData): SettleRecommendation {
        return SettleRecommendation(
            RepoUser(repo, data.user),
            data.amountsByGroup.associate { pair ->
                Pair(
                    RepoGroup(repo, pair.first),
                    pair.second
                )
            },
            data.totalAmount
        )
    }

    override suspend fun getAllSettleRecommendations(): AllSettleRecommendations = catchInternal {
        coroutineScope {
            val dUserRefresh = async { repo.userRepo.refreshUsers() }
            val dGroupsRefresh = async { repo.groupRepo.refreshAllGroups() }
            awaitAll(dUserRefresh,dGroupsRefresh)
        }
        val ret = repo.groupRepo.getAllSettleRecommendations()
        val byUser = ret.byUser.map(::settleRecommendationConvert)
        return AllSettleRecommendations(ret.total, byUser)
    }

    override suspend fun getSettleRecommendation(user: User): SettleRecommendation {
        return repo.groupRepo.getAllSettleRecommendations().byUser
            .first { it.user == UserId(user.id) }
            .let(::settleRecommendationConvert)
    }

    override suspend fun postTransactions(transactions: Map<Group, Transaction>) = catchInternal {
        for (transaction in transactions.values) {
            check(balanceChangesNotEmpty(transaction.balanceChanges))
        }
        repo.transactionRepo.postTransactions(
            transactions
                .map { GroupId(it.key.id) to it.value.toData() }
                .toMap()
        )
    }

    private suspend fun getSettings(): SettingsResponsePayload = catchInternal {
        serverSettings = serverSettings ?: remoteAPI.getSettings().unpack()
        return serverSettings!!
    }

    override suspend fun getCurrency(): Currency = getSettings().currency

    override suspend fun getOIDCSettings(): OIDCSettings = catchInternal {
        return OIDCSettings(getSettings().clientId, checkedDiscoveryUri(getSettings().discoveryUri))
    }

}

private fun checkedDiscoveryUri(settingsUri: URI): Uri {
    val raw = settingsUri.toString()
    if (URLUtil.isNetworkUrl(raw))
        return Uri.parse(raw)
    else {
        Log.wtf(::checkedDiscoveryUri.toString(), "Received invalid network url '$raw' as OIDC Discovery endpoint")
        throw BadResponseException("Invalid authorization config received from server.")
    }
}
