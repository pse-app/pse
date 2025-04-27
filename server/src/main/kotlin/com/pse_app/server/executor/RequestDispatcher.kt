package com.pse_app.server.executor

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.*
import com.pse_app.server.group.GroupManagement
import com.pse_app.server.transaction.TransactionManagement
import com.pse_app.server.user.UserManagement
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * The Reqeust Dispatcher implements [RequestExecutor].
 */
class RequestDispatcher(
    private val userManagement: UserManagement,
    private val groupManagement: GroupManagement,
    private val transactionManagement: TransactionManagement,
) : RequestExecutor {

    override fun deactivateUser(user: UserId): Result<Unit> =  traceResult("deactivateUser") {
        userManagement.deactivateUser(user)
    }

    private fun addMembershipInfo(actingUser: UserId, userInfo: UserInfo): Result<UserMembershipInfo> =
        Result.ap(
            groupManagement
                .getUserGroups(userInfo.id),
            groupManagement
                .getUserGroups(actingUser)
        ) { groups, knownGroups ->
            UserMembershipInfo(userInfo, groups.intersect(knownGroups))
        }

    private fun addMembershipInfo(groupInfo: GroupInfo): Result<GroupMembershipInfo> =
        groupManagement
            .getGroupMembers(groupInfo.id)
            .map { GroupMembershipInfo(groupInfo, it) }

    override fun retrieveUserMembershipInfo(actingUser: UserId, user: UserId): Result<UserMembershipInfo> {
        return traceResult("retrieveUserMembershipInfo") {
            userManagement
                .getUserInfo(user)
                .andThen { addMembershipInfo(actingUser, it) }
        }
    }

    override fun retrieveUserMembershipInfo(
        actingUser: UserId,
        users: Set<UserId>
    ): Result<Map<UserId, UserMembershipInfo>> = traceResult("retrieveUserMembershipInfo") {
        users
            .associateWith { retrieveUserMembershipInfo(actingUser, it) }
            .lift()
    }

    override fun updateUserDisplayName(user: UserId, displayName: String): Result<UserMembershipInfo> {
        return traceResult("updateUserDisplayName") {
            userManagement
                .setDisplayName(user, displayName)
                .andThen { retrieveUserMembershipInfo(user, user) }
        }
    }

    override fun listKnownUsers(user: UserId): Result<Set<UserId>> {
        return traceResult("listKnownUsers") {
            groupManagement.getUserGroups(user)
                .andThen { groups ->
                    groups
                        .map(groupManagement::getGroupMembers)
                        .lift()
                }
                .map {
                    it
                        .flatten()
                        .toSet()
                        .plus(user)
                }
        }
    }

    override fun listKnownGroups(user: UserId): Result<Set<GroupId>> {
        return traceResult("listKnownGroups") {
            groupManagement.getUserGroups(user)
        }
    }

    override fun createGroup(user: UserId, displayName: String): Result<GroupMembershipInfo> {
        return traceResult("createGroup") {
            groupManagement
                .createGroup(user, displayName)
                .andThen(::retrieveGroupMembershipInfo)
        }
    }

    override fun retrieveGroupMembershipInfo(group: GroupId): Result<GroupMembershipInfo> {
        return traceResult("retrieveGroupMembershipInfo") {
            groupManagement
                .getGroupInfo(group)
                .andThen(::addMembershipInfo)
        }
    }

    override fun retrieveGroupMembershipInfo(groups: Set<GroupId>): Result<Map<GroupId, GroupMembershipInfo>> {
        return traceResult("retrieveGroupMembershipInfo") {
            groups
                .associateWith(::retrieveGroupMembershipInfo)
                .lift()
        }
    }

    override fun updateGroupDisplayName(group: GroupId, displayName: String): Result<GroupMembershipInfo> {
        return traceResult("updateGroupDisplayName") {
            groupManagement
                .setDisplayName(group, displayName)
                .andThen { retrieveGroupMembershipInfo(group) }
        }
    }

    override fun regenerateInviteToken(group: GroupId): Result<String> {
        return traceResult("regenerateInviteToken") {
            groupManagement.regenerateInviteToken(group)
        }
    }

    override fun joinGroup(user: UserId, inviteToken: String): Result<GroupMembershipInfo> {
        return traceResult("joinGroup") {
            groupManagement
                .resolveInviteToken(inviteToken)
                .void { groupManagement.addMember(it, user) }
                .andThen(::retrieveGroupMembershipInfo)
        }
    }

    override fun getInviteInfo(inviteToken: String): Result<GroupMembershipInfo> {
        return traceResult("getInviteInfo") {
            groupManagement
                .resolveInviteToken(inviteToken)
                .andThen(::retrieveGroupMembershipInfo)
        }
    }

    override fun kickUser(group: GroupId, user: UserId): Result<Unit> {
        return traceResult("kickUser") {
            groupManagement.removeMember(group, user)
        }
    }

    override fun getTransactions(groups: Set<GroupId>): Result<Map<GroupId, List<Transaction>>> {
        return traceResult("getTransactions") {
            groups
                .associateWith(transactionManagement::getTransactions)
                .lift()
        }
    }

    override fun postTransactions(transaction: List<Transaction>): Result<Unit> {
        return traceResult("postTransactions") {
            transactionManagement.postTransactions(transaction)
        }
    }

    override fun calculateBalances(
        users: Set<UserId>,
        groups: Set<GroupId>,
    ): Result<Map<Pair<UserId, GroupId>, BigDec>> = transactionManagement.getBalances(users, groups)
    
    private inline fun <T>traceResult(name: String, action: () -> Result<T>): Result<T> {
        val result = action()
        logger.trace("{} results in {}", name, result)
        return result
    }
    
    companion object {
        val logger: Logger = LogManager.getLogger("request executor")
    }
}
