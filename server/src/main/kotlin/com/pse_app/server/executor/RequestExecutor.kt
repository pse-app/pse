package com.pse_app.server.executor

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.*

/**
 * The Request Executor interface.
 */
interface RequestExecutor {
    /**
     * Deactivates the [user].
     */
    fun deactivateUser(user: UserId): Result<Unit>

    /**
     * Retrieves [UserMembershipInfo] of the [user]. Fails if the [actingUser] does not know the
     * [user].
     */
    fun retrieveUserMembershipInfo(actingUser: UserId, user: UserId): Result<UserMembershipInfo>

    /**
     * Retrieves [UserMembershipInfo] of the [users]. Fails if the [actingUser] does not know some
     * user.
     */
    fun retrieveUserMembershipInfo(actingUser: UserId, users: Set<UserId>): Result<Map<UserId, UserMembershipInfo>>

    /**
     * Updates the display name of the [user].
     */
    fun updateUserDisplayName(user: UserId, displayName: String): Result<UserMembershipInfo>

    /**
     * Lists all users known to [user].
     */
    fun listKnownUsers(user: UserId): Result<Set<UserId>>

    /**
     * Lists all groups known to [user].
     */
    fun listKnownGroups(user: UserId): Result<Set<GroupId>>

    /**
     * Creates a new group with the specified [displayName] and an initial member [user].
     */
    fun createGroup(user: UserId, displayName: String): Result<GroupMembershipInfo>

    /**
     * Retrieves [GroupMembershipInfo] of [group].
     */
    fun retrieveGroupMembershipInfo(group: GroupId): Result<GroupMembershipInfo>

    /**
     * Retrieves [GroupMembershipInfo] of [groups].
     */
    fun retrieveGroupMembershipInfo(groups: Set<GroupId>): Result<Map<GroupId, GroupMembershipInfo>>

    /**
     * Updates the display name of [group].
     */
    fun updateGroupDisplayName(group: GroupId, displayName: String): Result<GroupMembershipInfo>

    /**
     * Regenerates the invite token of [group].
     */
    fun regenerateInviteToken(group: GroupId): Result<String>

    /**
     * Attempts to make [user] a member of the group associated with [inviteToken].
     */
    fun joinGroup(user: UserId, inviteToken: String): Result<GroupMembershipInfo>

    /**
     * Attempts to kick [user] from [group]. Fails if the [user]s balance is not zero.
     */
    fun kickUser(group: GroupId, user: UserId): Result<Unit>

    /**
     * Retrieves all transactions in all [groups].
     */
    fun getTransactions(groups: Set<GroupId>): Result<Map<GroupId, List<Transaction>>>

    /**
     * Posts a [transaction].
     */
    fun postTransactions(transaction: List<Transaction>): Result<Unit>

    /**
     * Calculates balances of [users] inside [groups].
     */
    fun calculateBalances(users: Set<UserId>, groups: Set<GroupId>): Result<Map<Pair<UserId, GroupId>, BigDec>>

    /**
     * Retrieves [GroupMembershipInfo] of the group associated with the [inviteToken].
     */
    fun getInviteInfo(inviteToken: String): Result<GroupMembershipInfo>
}
