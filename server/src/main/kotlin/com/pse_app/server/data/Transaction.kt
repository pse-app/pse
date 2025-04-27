package com.pse_app.server.data

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import java.time.Instant

/**
 * Models a single transaction in the system.
 *
 * @param group The group the transaction belongs to
 * @param name The name given to the transaction
 * @param comment The comment added to the transaction, if any
 * @param timestamp The timestamp at which the transaction was recorded by the system
 * @param balanceChanges The changes in balance for all users involved in the transactions
 * @param originatingUser The user from which this transaction originated
 */
sealed class Transaction(
    open val group: GroupId,
    open val name: String,
    open val comment: String?,
    open val timestamp: Instant,
    open val balanceChanges: Map<UserId, BigDec>,
    open val originatingUser: UserId
)

/**
 * Models a simple payment (a transaction without a total expense value).
 */
data class Payment(
    override val group: GroupId,
    override val name: String,
    override val comment: String?,
    override val timestamp: Instant,
    override val balanceChanges: Map<UserId, BigDec>,
    override val originatingUser: UserId
) : Transaction(group, name, comment, timestamp, balanceChanges, originatingUser)

/**
 * Models an expense (a transaction with a total expense value).
 * 
 * @param expenseAmount The total expense value.
 */
data class Expense(
    override val group: GroupId,
    override val name: String,
    override val comment: String?,
    override val timestamp: Instant,
    override val balanceChanges: Map<UserId, BigDec>,
    override val originatingUser: UserId,
    val expenseAmount: BigDec
) : Transaction(group, name, comment, timestamp, balanceChanges, originatingUser)
