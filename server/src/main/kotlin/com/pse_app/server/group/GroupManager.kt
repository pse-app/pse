package com.pse_app.server.group

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.*
import com.pse_app.server.database.PostgreSQL
import com.pse_app.server.database.SqlTypes
import com.pse_app.server.database.Tables
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.support.postgresql.InsertOrUpdateStatementBuilder
import org.ktorm.support.postgresql.insertOrUpdate
import java.math.BigInteger
import java.sql.SQLException
import java.util.*
import kotlin.random.Random

/**
 * The Group Manager implements [GroupManagement] and provides the [authorizer].
 */
class GroupManager(private val db: Database) : GroupManagement {

    /**
     * Implementation of [Authorizer].
     */
    val authorizer: Authorizer = GroupAuthorizer(db, this)

    private class GroupAuthorizer(private val db: Database, private val groupManager: GroupManager) : Authorizer {
        override fun allUsersKnown(actingUser: UserId, users: Set<UserId>): Result<Unit> = db.useTransaction {
            logger.trace("allUsersKnown(acting={}, users={})", actingUser, users)
            groupManager.userIsActive(actingUser)
                .andThen { groupManager.getUserGroups(actingUser) }
                .andThen { potentialCommonGroups ->
                    val knownUsers = mutableSetOf(actingUser)
                    // this is needed because inList has a syntax error when applied to an empty collection
                    if (potentialCommonGroups.isNotEmpty()) {
                        val usersInCommonGroups = db.from(Tables.Membership)
                            .select(Tables.Membership.userId)
                            .where { Tables.Membership.groupId inList potentialCommonGroups }
                            .map { it[Tables.Membership.userId]!! }
                            .toSet()
                        knownUsers.addAll(usersInCommonGroups)
                    }
                    if (knownUsers.containsAll(users)) Result.Success(Unit)
                    else Result.Error(ErrorCategory.UserNotFound, "Could not find some user")
                }
        }

        override fun allGroupsKnown(actingUser: UserId, groups: Set<GroupId>): Result<Unit> = db.useTransaction {
            logger.trace("allGroupsKnown(acting={}, groups={})", actingUser, groups)
            groupManager.userIsActive(actingUser).mapErrorCategory { _, _ -> ErrorCategory.GroupNotFound }
                .andThen { groupManager.getUserGroups(actingUser) }
                .andThen { knownGroups ->
                    if (knownGroups.containsAll(groups)) Result.Success(Unit)
                    else Result.Error(ErrorCategory.GroupNotFound, "Could not find some Group")
                }
        }

        override fun groupKnown(actingUser: UserId, group: GroupId): Result<Unit> = db.useTransaction {
            logger.trace("groupKnown(acting={}, group={})", actingUser, group)
            groupManager.userIsActive(actingUser).mapErrorCategory { _, _ -> ErrorCategory.GroupNotFound }
                .andThen { groupManager.getUserGroups(actingUser) }
                .andThen { knownGroups ->
                    if (knownGroups.contains(group)) Result.Success(Unit)
                    else unknownGroup(group)
                }
        }
    }

    override fun getGroupInfo(group: GroupId): Result<GroupInfo> = db.useTransaction {
        logger.trace("getGroupInfo(group={})", group)
        db.from(Tables.Groups)
            .leftJoin(Tables.RecentTransactionTimestamps, on = Tables.Groups.id eq Tables.RecentTransactionTimestamps.groupId)
            .select(
                Tables.Groups.displayName,
                Tables.Groups.inviteToken,
                Tables.RecentTransactionTimestamps.transactionTimestamp
            )
            .where(Tables.Groups.id eq group)
            .map { row ->
                GroupInfo(
                    id = group,
                    displayName = row[Tables.Groups.displayName]!!,
                    inviteToken = row[Tables.Groups.inviteToken]!!,
                    mostRecentTransaction = row[Tables.RecentTransactionTimestamps.transactionTimestamp]
                )
            }
            .asIterable()
            .firstOrNull()
            .errIfNull(unknownGroup(group))
    }

    override fun setDisplayName(group: GroupId, displayName: String): Result<Unit> = db.useTransaction {
        logger.trace("setDisplayName(group={}, displayName={})", group, displayName)
        val affected = db.useTransaction {
            db.update(Tables.Groups) {
                set(it.displayName, displayName)
                where { it.id eq group }
            }
        }
        if (affected == 0) unknownGroup(group)
        else Result.Success(Unit)
    }

    private fun InsertOrUpdateStatementBuilder.addMember(user: UserId, group: GroupId) {
        set(Tables.Membership.userId, user)
        set(Tables.Membership.groupId, group)
        onConflict { doNothing() }
    }

    @Suppress("RedundantUnitExpression")
    private fun userExists(user: UserId): Result<Unit> =
        db.from(Tables.Users)
            .select(Tables.Users.id)
            .where { Tables.Users.id eq user }
            .map { Unit }
            .asIterable()
            .firstOrNull()
            .errIfNull(ErrorCategory.UserNotFound, "Could not find User with ID '$user'")

    @Suppress("RedundantUnitExpression")
    private fun groupExists(group: GroupId): Result<Unit> =
        db.from(Tables.Groups)
            .select(Tables.Groups.id)
            .where { Tables.Groups.id eq group }
            .map { Unit }
            .asIterable()
            .firstOrNull()
            .errIfNull(unknownGroup(group))

    override fun addMember(group: GroupId, user: UserId): Result<Unit> = db.useTransaction {
        logger.trace("addMember(group={}, user={})", user, group)
        Result.ap(
            userExists(user),
            groupExists(group)
        ) { _, _ ->
            db.insertOrUpdate(Tables.Membership) {
                addMember(user, group)
            }
            Unit
        }
    }

    private fun generateInviteToken(): String{
        val data = BigInteger(1, byteArrayOf(0xFE.toByte()).plus(Random.nextBytes(INVITE_TOKEN_BYTE_LENGTH)))
        return data.toString(INVITE_TOKEN_RADIX)
    }

    override fun createGroup(initialMember: UserId, displayName: String): Result<GroupId> = db.useTransaction {
        logger.trace("createGroup(initiator={}, displayName={})", initialMember, displayName)
        Result.ap(
            userExists(initialMember),
            userIsActive(initialMember),
        ) { _, _ ->
            val group = GroupId(UUID.randomUUID())
            Result.tryTo<Int, SQLException> {
                db.insert(Tables.Groups) {
                    set(Tables.Groups.id, group)
                    set(Tables.Groups.displayName, displayName)
                    set(Tables.Groups.inviteToken, generateInviteToken())
                }
            }
                .mapError { "Failed to create group" }
                .map {
                    db.insertOrUpdate(Tables.Membership) {
                        addMember(initialMember, group)
                    }
                    group
                }
        }
            .flatten()
    }

    override fun regenerateInviteToken(group: GroupId): Result<String> = db.useTransaction {
        logger.trace("regenerateInviteToken(group={})", group)
        val inviteToken = generateInviteToken()
        Result.tryTo<Int, SQLException> {
            db.update(Tables.Groups) {
                set(Tables.Groups.inviteToken, inviteToken)
                where { it.id eq group }
            }
        }
            .mapError { "Failed regenerating invite token" }
            .andThen { affected ->
                if (affected == 0) unknownGroup(group)
                else Result.Success(inviteToken)
            }
    }

    override fun getUserGroups(user: UserId): Result<Set<GroupId>> = db.useTransaction {
        logger.trace("getUserGroups(user={})", user)
        userExists(user)
            .map {
                db.from(Tables.Membership)
                    .select(Tables.Membership.groupId)
                    .where { Tables.Membership.userId eq user }
                    .map { it[Tables.Membership.groupId]!! }
                    .toSet()
            }
    }

    override fun getGroupMembers(group: GroupId): Result<Set<UserId>> = db.useTransaction {
        logger.trace("getGroupMembers(group={})", group)
        groupExists(group)
            .map {
                db.from(Tables.Membership)
                    .select(Tables.Membership.userId)
                    .where { Tables.Membership.groupId eq group }
                    .map { it[Tables.Membership.userId]!! }
                    .toSet()
            }
    }

    override fun resolveInviteToken(inviteToken: String): Result<GroupId> = db.useTransaction {
        logger.trace("resolveInviteToken(inviteToken={})", inviteToken)
        db.from(Tables.Groups)
            .select(Tables.Groups.id)
            .where { Tables.Groups.inviteToken eq inviteToken }
            .map { it[Tables.Groups.id]!! }
            .firstOrNull()
            .errIfNull(
                ErrorCategory.InvalidInvite,
                "Invite token '$inviteToken' does not belong to any existing group"
            )
    }

    override fun removeMember(group: GroupId, user: UserId): Result<Unit> = db.useTransaction {
        logger.trace("removeMember(group={}, user={})", group, user)
        Result.ap(
            userExists(user),
            groupExists(group),
            balanceIsZero(user, group)
        ) { _, _, _ ->
            val affected = db.delete(Tables.Membership) {
                (it.groupId eq group) and (it.userId eq user)
            }
            if (affected == 1 && allMembersDeactivated(group)) {
                logger.trace("removeMember: Group has only deactivated users, deleting.")
                db.delete(Tables.Membership) {
                    it.groupId eq group
                }
                db.delete(Tables.Transactions) {
                    it.groupId eq group
                }
                db.delete(Tables.Groups) {
                    it.id eq group
                }
            }
        }
    }

    @Suppress("RedundantUnitExpression")
    private fun allMembersDeactivated(group: GroupId): Boolean =
        db.from(Tables.Membership)
            .leftJoin(
                Tables.ActiveUsers,
                on = (Tables.Membership.userId eq Tables.ActiveUsers.id)
            )
            .select(Tables.ActiveUsers.id)
            .where((Tables.Membership.groupId eq group) and Tables.ActiveUsers.id.isNotNull())
            .map { Unit }
            .isEmpty()

    @Suppress("RedundantUnitExpression")
    private fun userIsActive(user: UserId): Result<Unit> =
        db.from(Tables.ActiveUsers)
            .select(Tables.ActiveUsers.id)
            .where { Tables.ActiveUsers.id eq user }
            .map { Unit }
            .firstOrNull()
            .errIfNull(ErrorCategory.UserNotFound, "User '$user' is deactivated")

    private fun balanceIsZero(user: UserId, group: GroupId): Result<Unit> {
        val balanceChangeSum = PostgreSQL.coalesce(
            SqlTypes.BigDecSqlType, listOf(
                sum(Tables.BalanceChanges.amount),
                PostgreSQL.bigDecExpression(BigDec("0"))
            )
        ).aliased("balance_change_sum")

        return db.from(Tables.Membership)
            .leftJoin(Tables.Transactions, on = Tables.Membership.groupId eq Tables.Transactions.groupId)
            .leftJoin(
                Tables.BalanceChanges,
                on = (Tables.Transactions.id eq Tables.BalanceChanges.transactionId)
                        and (Tables.Membership.userId eq Tables.BalanceChanges.userId)
            )
            .select(balanceChangeSum)
            .where((Tables.Membership.userId eq user) and (Tables.Membership.groupId eq group))
            .groupBy(Tables.Membership.groupId, Tables.Membership.userId)
            .map { row -> row[balanceChangeSum]!! }
            .singleOrNull()
            ?.let {
                if (it == BigDec(0)) Result.Success(Unit)
                else Result.Error(ErrorCategory.PreconditionFailed, "Balance not zero.")
            }
            ?: Result.Success(Unit)
    }
    
    companion object {
        private val logger: Logger = LogManager.getLogger()

        private const val INVITE_TOKEN_RADIX = 36
        private const val INVITE_TOKEN_BYTE_LENGTH = 36
    }
}

private fun unknownGroup(group: GroupId): Result.Error =
    Result.Error(ErrorCategory.GroupNotFound, "Could not find Group with ID '$group'")
