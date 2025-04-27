package com.pse_app.client.model.facade

import com.pse_app.client.model.exceptions.InvalidInviteLink
import com.pse_app.client.model.exceptions.LoginRejectedException
import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.exceptions.NetworkException
import com.pse_app.client.model.exceptions.UnexpectedServerException
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger
import java.util.Currency

/**
 * Main interface for interaction with model
 */
interface ModelFacade {
    /**
     * Returns true if accessing protected endpoints is possible.
     * Will try to refresh stale session if one exists.
     * @throws LoginRejectedException
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Create session and create user if necessary.
     * @throws LoginRejectedException
     */
    suspend fun login(idToken: String)
    /** Delete active session. No failure in case no session exists. */
    suspend fun logout()
    /**
     * Set display name of active user.
     * @throws ModelException
     */
    suspend fun setUserDisplayName(name: String)
    /**
     * Deactivate account of active user.
     * @throws ModelException
     */
    suspend fun deactivateActiveUser()

    /**
     * Flow on currently active user.
     * Only updates when user changes through login/logout.
     */
    fun observeActiveUser(): Flow<User?>

    /**
     * Refresh all data connected to active user,
     * including list of groups in which the active user is a member.
     * @throws ModelException
     */
    suspend fun refreshActiveUser()

    /**
     * Refresh data for list of users or all known users in case no list is provided.
     * @throws ModelException
     */
    suspend fun refreshUsers(users: List<User>?=null)

    /**
     * Flow on list of groups in which the active user is a member.
     * Groups have no specific ordering!
     */
    fun observeGroups(): Flow<List<Group>?>

    /**
     * Returns a reference to the group with the given id. There may be no data available for that group.
     */
    fun getGroupById(id: String): Group

    /**
     * Returns a reference to the user with the given id. There may be no data available for that group.
     */
    fun getUserById(id: String): User


    /**
     * Create a new group with name.
     * @throws ModelException
     */
    suspend fun createGroup(name: String): Group

    /**
     * Join group with invite token.
     * @throws ModelException
     */
    suspend fun joinGroup(inviteToken: String): Group

    /**
     * Get info of group with invite token
     * @throws ModelException
     * @throws InvalidInviteLink
     */
    suspend fun getGroupInviteInfo(inviteToken: String): GroupInviteInfo

    /**
     * Get proposed settlement for all known users
     * @throws ModelException
     */
    suspend fun getAllSettleRecommendations(): AllSettleRecommendations

    /**
     * Get proposed settlement with one user
     * @throws ModelException
     */
    suspend fun getSettleRecommendation(user: User): SettleRecommendation

    /**
     * Post some transactions.
     * @throws ModelException
     */
    suspend fun postTransactions(transactions: Map<Group, Transaction>)

    /**
     * Gets oidc provider settings
     * @throws UnexpectedServerException
     * @throws NetworkException
     */
    suspend fun getOIDCSettings(): OIDCSettings

    /**
     * Splits the total among participating users. Total must be non-negative.
     * The scale of the result values is taken from the input value.
     */
    fun splitEvenly(total: BigDec, participatingUsers: List<User>): Map<User, BigDec> {
        check(participatingUsers.isNotEmpty())
        check(total >= BigDec.ZERO)
        val scale = total.value.scale()
        val unscaled = total.value.unscaledValue()
        var (div, rem) = unscaled.divideAndRemainder(BigInteger.valueOf(participatingUsers.size.toLong()))
        val out: MutableMap<User, BigDec> = mutableMapOf()
        for (user in participatingUsers) {
            if (rem.toLong() > 0) {
                out[user] = BigDec(div.plus(BigInteger.ONE), scale)
                rem--
            } else {
                out[user] = BigDec(div, scale)
            }
        }
        return out.toMap()
    }

    /**
     * Returns the server's configured currency
     * @throws UnexpectedServerException
     * @throws NetworkException
     */
    suspend fun getCurrency(): Currency
}
