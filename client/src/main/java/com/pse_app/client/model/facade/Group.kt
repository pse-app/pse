package com.pse_app.client.model.facade

import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Represents a specific group provided by the model facade.
 */
interface Group {
    /**
     * Uniquely identifies group
     */
    val id: String

    /**
     * A flow on the group display name.
     * Emits a first value immediately on collection.
     * Emits null if group no longer valid.
     */
    val displayName: Flow<String?>

    /**
     * A flow on the invite URL of the group.
     * Emits a first value immediately on collection.
     * Emits null if group no longer valid.
     */
    val inviteUrl: Flow<String?>

    /**
     * A flow on balance of the active user to this group.
     * Emits a first value immediately on collection.
     * Emits null if group no longer valid.
     */
    val personalBalance: Flow<BigDec?>

    /**
     * A flow on the balance of each user in the group.
     * Emits a first value immediately on collection.
     * Emits empty map if group no longer valid.
     */
    val balances: Flow<Map<User, BigDec>>

    /**
     * A flow on the members of the group.
     * Emits a first value immediately on collection.
     * Emits null if group no longer valid.
     */
    val members: Flow<List<User>?>

    /**
     * A flow on the transactions of the group.
     * Emits a first value immediately on collection.
     * Emits null if group no longer valid.
     */
    val transactions: Flow<List<Transaction>?>

    /**
     * A flow on the timestamp of the most recent transaction in this group.
     * Emits a first value immediately on collection.
     * Emits null if group no longer valid or no transactions exist.
     */
    val mostRecentTransaction: Flow<Instant?>

    /**
     * Fetches new values for all of the observable group properties,
     * including transaction properties.
     *
     * @throws ModelException
     */
    suspend fun refresh()

    /**
     * Fetches new values for all of the observable group properties,
     * excluding transaction properties.
     *
     * @throws ModelException
     */
    suspend fun refreshPartial()

    /**
     * Changes the display name of the group.
     *
     * @throws ModelException
     */
    suspend fun setDisplayName(name: String)

    /**
     * Removes user from group.
     *
     * @throws ModelException
     */
    suspend fun kickUser(user: User)

    /**
     * Generates a new invite link for the group.
     *
     * @throws ModelException
     */
    suspend fun regenerateInviteLink()

    /**
     * Posts new expense with every user contributing positive [split] of the [total] payed by [originatingUser]
     */
    suspend fun addExpense(
        name: String,
        comment: String?,
        split: Map<User, BigDec>,
        originatingUser: User,
        total: BigDec
    )

    /**
     * Posts new payment in group.
     * [externalBalanceChange] contains amounts payed IRL to participating users by [payingUser]
     * and may therefore not contain [payingUser] as key.
     * Balance for [payingUser] is changed accordingly to balance transaction.
     *
     * Note difference between [payingUser] and [originatingUser], the latter being the active user
     */
    suspend fun addPayment(
        name: String,
        comment: String?,
        externalBalanceChange: Map<User, BigDec>,
        payingUser: User,
        originatingUser: User
    )
}
