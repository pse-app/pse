package com.pse_app.server.user

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.server.data.*
import com.pse_app.server.database.Tables
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.expression.ArgumentExpression
import org.ktorm.schema.DecimalSqlType
import org.ktorm.support.postgresql.insertOrUpdate
import java.net.URI
import java.security.SecureRandom
import java.time.Instant

/**
 * The User Manager implements [UserManagement] and provides the [authenticator].
 */
class UserManager(private val db: Database) : UserManagement {

    /**
     * Implementation of [Authenticator].
     */
    val authenticator: Authenticator = UserAuthenticator()

    override fun getUserInfo(user: UserId): Result<UserInfo> = db.useTransaction {
        logger.trace("getUserInfo(user={})", user)
        db.from(Tables.Users)
            .leftJoin(Tables.ActiveUsers, Tables.Users.id eq Tables.ActiveUsers.id)
            .select(
                Tables.Users.id,
                Tables.ActiveUsers.displayName,
                Tables.ActiveUsers.profilePictureUrl,
            )
            .where(Tables.Users.id eq user)
            .map { row ->
                UserInfo(
                    id = row[Tables.Users.id]!!,
                    displayName = row[Tables.ActiveUsers.displayName] ?: row[Tables.Users.id]!!.id,
                    profilePicture = row[Tables.ActiveUsers.profilePictureUrl],
                )
            }
            .asIterable()
            .firstOrNull()
            .errIfNull(unknownUser(user))
    }

    override fun setDisplayName(user: UserId, displayName: String): Result<Unit> = db.useTransaction {
        logger.trace("setDisplayName(user={}, displayName={})", user, displayName)
        val affected = db.update(Tables.ActiveUsers) {
            set(it.displayName, displayName)
            where { it.id eq user }
        }
        if (affected == 0) unknownUser(user)
        else Result.Success(Unit)
    }

    override fun deactivateUser(user: UserId): Result<Unit> = db.useTransaction {
        logger.trace("deactivateUser(user={})", user)
        db.delete(Tables.RefreshTokens) {
            it.userId eq user
        }
        val affected = db.delete(Tables.ActiveUsers) {
            it.id eq user
        }
        if (affected == 0)
            return unknownUser(user)
        val groups = groupsToBeDeletedAfterDeactivatingUser(user)
        // needed because ktorm generates a sql syntax error when using inList on an empty collection
        if (groups.isNotEmpty()) {
            logger.trace("deactivateUser: Identified groups to delete: {}", groups)
            db.delete(Tables.Membership) {
                it.groupId inList groups
            }
            db.delete(Tables.Transactions) {
                it.groupId inList groups
            }
            db.delete(Tables.Groups) {
                it.id inList groups
            }
        }
        Result.Success(Unit)
    }

    private fun groupsToBeDeletedAfterDeactivatingUser(user: UserId): Set<GroupId> {
        val possibleGroupsToRemove = db.from(Tables.Membership)
            .select(Tables.Membership.groupId)
            .where(Tables.Membership.userId eq user)
            .map { it[Tables.Membership.groupId]!! }
            .toSet()

        // needed because ktorm generates a sql syntax error when using inList on an empty collection
        if (possibleGroupsToRemove.isEmpty())
            return possibleGroupsToRemove

        return db.from(Tables.Membership)
            .innerJoin(
                Tables.ActiveUsers,
                on = Tables.Membership.userId eq Tables.ActiveUsers.id
            )
            .rightJoin(
                Tables.Groups,
                on = Tables.Groups.id eq Tables.Membership.groupId)
            .select(Tables.Groups.id)
            .where((Tables.Groups.id inList possibleGroupsToRemove) and Tables.ActiveUsers.id.isNull())
            .map { it[Tables.Groups.id]!! }
            .toSet()
    }


    private fun unknownUser(user: UserId) =
        Result.Error(ErrorCategory.UserNotFound, "Could not find User with ID '$user'")

    private inner class UserAuthenticator : Authenticator {
        private val randomness = SecureRandom.getInstanceStrong()

        override fun createSession(
            user: UserId,
            displayName: String,
            profilePicture: URI?,
        ): Result<AuthenticationResult> = db.useTransaction {
            logger.trace(
                "createSession(user={}, displayName={}, profilePicture={})",
                user,
                displayName,
                profilePicture
            )
            val lastLogin = Instant.now()

            val token = RefreshToken.generate(user, randomness)
            val hash = token.generateHash()

            db.insertOrUpdate(Tables.Users) {
                set(it.id, user)
                set(it.lastLogin, lastLogin)
                onConflict(Tables.Users.id) {
                    set(it.lastLogin, lastLogin)
                }
            }
            db.insertOrUpdate(Tables.ActiveUsers) {
                set(it.id, user)
                set(it.displayName, displayName)
                set(it.profilePictureUrl, profilePicture)
                onConflict(Tables.Users.id) {
                    if (profilePicture != null)
                        set(it.profilePictureUrl, profilePicture)
                    else
                        doNothing()
                }
            }
            db.insert(Tables.RefreshTokens) {
                set(it.userId, user)
                set(it.token, hash)
            }
            getUserInfo(user).map(token::toAuthResult)
        }

        override fun refreshSession(refreshToken: String): Result<AuthenticationResult> = db.useTransaction {
            logger.trace("refreshSession(refreshToken={})", refreshToken)
            val old = when (val decoded = RefreshToken.decode(refreshToken)) {
                is Result.Success -> decoded.value
                is Result.Error -> return Result.Error(
                    ErrorCategory.InvalidToken,
                    "Malformed refresh token"
                )
            }

            val user = old.userId
            val new = RefreshToken.generate(user, randomness)

            val affected = db.update(Tables.RefreshTokens) {
                set(it.token, new.generateHash())
                where { (it.userId eq user) and (it.token eq old.generateHash()) }
            }
            if (affected == 0) return Result.Error(
                ErrorCategory.InvalidToken,
                "Invalid refresh token"
            )
            getUserInfo(user).map(new::toAuthResult)
        }

        @Suppress("RedundantUnitExpression")
        override fun authenticate(user: UserId): Result<Unit> = db.useTransaction {
            logger.trace("authenticate(user={})", user)
            db.from(Tables.ActiveUsers)
                .select(ArgumentExpression(1.toBigDecimal(), DecimalSqlType))
                .where(Tables.ActiveUsers.id eq user)
                .map { Unit }
                .singleOrNull()
                .errIfNull(unknownUser(user))
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger()
    }
}
