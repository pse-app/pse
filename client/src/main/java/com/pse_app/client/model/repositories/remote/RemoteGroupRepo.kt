package com.pse_app.client.model.repositories.remote

import android.util.Log
import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.exceptions.BalanceNotZeroException
import com.pse_app.client.model.exceptions.NoActiveUserException
import com.pse_app.client.model.exceptions.ReferenceInvalidException
import com.pse_app.client.model.repositories.GroupRepo
import com.pse_app.client.model.repositories.GroupRepoObservables
import com.pse_app.client.model.repositories.data.AllRecommendationData
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.repositories.local.LocalGroupRepo
import com.pse_app.common.dto.BalancesRequestPayload
import com.pse_app.common.dto.ChangeDisplayNameRequestPayload
import com.pse_app.common.dto.CreateGroupRequestPayload
import com.pse_app.common.dto.GroupInfoResponsePayload
import com.pse_app.common.util.BigDec
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import com.pse_app.common.dto.UserId as DTOUserID

/**
 * Repository for group related actions and data, excluding transactions.
 * Single source of truth for [GroupData]
 *
 * Retrieves data from [RemoteAPI]
 */
class RemoteGroupRepo(
    private val remoteAPI: RemoteAPI,
    private val repo: RemoteRepo,
    private val localGroupRepo: LocalGroupRepo = LocalGroupRepo()
) : GroupRepo, GroupRepoObservables by localGroupRepo {
    private suspend fun getActiveUserId(): UserId {
        return repo.userRepo.observeActiveUserId().first() ?: run {
            Log.wtf(this::class.simpleName, "Active User not there?")
            throw NoActiveUserException()
        }
    }

    private fun shortDataFromResponse(
        response: GroupInfoResponsePayload,
        balances: Map<UserId, BigDec>
    ): GroupData {
        return GroupData(
            response.id.cast(),
            response.displayName,
            response.inviteUri.toString(),
            response.members.map{it.cast()},
            response.members.associate { it.cast() to BigDec(0) } + balances,
            response.mostRecentTransaction
        )
    }

    private suspend fun fetchBalances(groupId: GroupId): Deferred<Map<UserId, BigDec>> = coroutineScope {
        async {
            val balances = remoteAPI
                .getBalances(BalancesRequestPayload(null, setOf(groupId.cast())))
                .unpack().balances
            balances
                .filter { (k, _) -> k.second.cast() == groupId }
                .map { el -> el.key.first.cast() to el.value }
                .toMap()
        }
    }

    private suspend fun fetchAllBalances(): Deferred<Map<GroupId, Map<UserId, BigDec>>> = coroutineScope {
        async {
            val response = remoteAPI.getBalances(BalancesRequestPayload(
                null,
                null
            )).unpack().balances
            val output = mutableMapOf<GroupId, MutableMap<UserId, BigDec>>()
            response.forEach{ output[it.key.second.cast()] = mutableMapOf() }
            for ((k, v) in response) {
                output.getValue(k.second.cast())[k.first.cast()] = v
            }
            output.map { (k, v) -> k to v.toMap() }.toMap()
        }
    }

    override suspend fun refreshAllGroups() = coroutineScope {
        val allBalances = fetchAllBalances().await()
        val groupIds = allBalances.keys
        val castedGroupIds = groupIds.map(GroupId::cast).toSet()
        val groups = remoteAPI.getGroups(castedGroupIds).unpack().values

        val groupData = groups.map {
            shortDataFromResponse(it, allBalances
                .getValue(it.id.cast())
                .filter { (k, _) -> it.members.contains(k.cast()) }
            )
        }
        localGroupRepo.setAllGroupData(groupData)
    }

    override suspend fun refreshGroup(groupId: GroupId, fetchBalances: Boolean) = coroutineScope {
        val groupResponse = remoteAPI
            .getGroups(setOf(groupId.cast())).unpack()
            .getValue(groupId.cast())
        val members = groupResponse.members.map(DTOUserID::cast)

        val balances: Map<UserId, BigDec>
        if (fetchBalances) {
            val userRefresh = async {
                repo.userRepo.refreshUsers(members)
            }
            val dBalances = async {
                fetchBalances(groupId).await()
            }
            userRefresh.await()
            balances = dBalances.await()
        } else {
            val allBalances = repo.transactionRepo.getDerivedBalances(groupId)
            repo.userRepo.refreshUsers(members)
            repo.userRepo.touchUsers(allBalances.keys)
            balances = allBalances.filterKeys { members.contains(it) }
        }

        localGroupRepo.setGroupData(shortDataFromResponse(
            groupResponse,
            balances
        ))
    }

    override suspend fun createGroup(name: String): GroupId {
        val activeUserId = getActiveUserId()
        return remoteAPI
            .createGroup(CreateGroupRequestPayload(name)).unpack()
            .let { shortDataFromResponse(it, mapOf(activeUserId to BigDec(0))) }
            .also { localGroupRepo.setGroupData(it) }
            .groupId
    }

    override suspend fun joinGroup(inviteToken: String): GroupId {
        val response = remoteAPI.joinGroup(inviteToken).unpack()
        val balances = fetchBalances(response.id.cast())
        return shortDataFromResponse(response, balances.await())
            .also { localGroupRepo.setGroupData(it) }
            .groupId
    }

    override suspend fun regenerateInviteLink(groupId: GroupId) {
        val groupData = observeGroup(groupId).first()
            ?: throw ReferenceInvalidException("Regenerating invite for Group that doesn't exist?")
        val response = remoteAPI.regenerateGroupInviteLink(groupId.id).unpack()
        localGroupRepo.setGroupData(groupData.copy(
            inviteUrl = response.newInviteUri.toString()
        ))
    }

    override suspend fun kickUser(groupId: GroupId, userId: UserId) {
        val activeUserId = getActiveUserId()
        val groupData = observeGroup(groupId).first()
            ?: throw ReferenceInvalidException("Kicking from Group that doesn't exist?")

        remoteAPI.kickUser(groupId.id, userId.id).also {
            if (it.status == HttpStatusCode.PreconditionFailed) {
                throw BalanceNotZeroException(isKick = userId != activeUserId)
            }
        }.unpack()

        if (userId == activeUserId) {
            localGroupRepo.deleteGroupData(groupId)
        } else {
            localGroupRepo.setGroupData(groupData.copy(
                members = groupData.members - listOf(userId).toSet()
            ))
        }
    }

    override suspend fun setDisplayName(groupId: GroupId, name: String) {
        val groupData = observeGroup(groupId).first()
            ?: throw ReferenceInvalidException("Setting name of group that doesn't exist?")
        val response = remoteAPI.setGroupDisplayName(
            groupId.id, ChangeDisplayNameRequestPayload(name)).unpack()
        localGroupRepo.setGroupData(groupData.copy(
            displayName = response.displayName
        ))
    }

    private fun bigDecMin(a: BigDec, b: BigDec): BigDec {
        return if (a < b) {
            a
        } else {
            b
        }
    }

    private fun calculateSettlement(ownBalance: BigDec, otherBalance: BigDec): BigDec {
        return if (ownBalance < BigDec(0) && BigDec(0) < otherBalance) {
            -bigDecMin(otherBalance, -ownBalance)
        }
        else if (otherBalance < BigDec(0) && BigDec(0) < ownBalance) {
            bigDecMin(ownBalance, -otherBalance)
        } else {
            BigDec(0)
        }
    }

    override suspend fun getAllSettleRecommendations(): AllRecommendationData {
        val activeUserId = getActiveUserId()

        val groups = (observeAllGroupIds().first() ?: listOf())
            .mapNotNull { observeGroup(it).first() }

        val dataByUser: MutableMap<UserId, MutableList<Pair<GroupId, BigDec>>> = mutableMapOf()
        val activeUserBalances = groups.associate {
            it.groupId to it.memberBalances.getValue(activeUserId)
        }

        for (group in groups) {
            for (entry in group.memberBalances.entries) {
                val userId = entry.key
                val groupId = group.groupId

                if (userId == activeUserId) continue
                if (!dataByUser.containsKey(userId)) {
                    dataByUser[userId] = mutableListOf()
                }
                if (activeUserBalances.containsKey(groupId)) {
                    dataByUser.getValue(userId).add(
                        Pair(groupId, calculateSettlement(activeUserBalances.getValue(groupId), entry.value))
                    )
                }
            }
        }

        val recommendations = dataByUser.map { SettleRecommendationData (
            it.key,
            it.value.toList(),
            it.value.fold(BigDec(0)) { acc, e -> acc+e.second }
        ) }
        return AllRecommendationData(
            activeUserBalances.values.fold(BigDec.ZERO){acc,e -> acc+e},
            recommendations
        )
    }
}
