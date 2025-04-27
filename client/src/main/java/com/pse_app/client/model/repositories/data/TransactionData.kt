package com.pse_app.client.model.repositories.data

import com.pse_app.common.util.BigDec
import java.time.Instant

/**
 * Represents a group transaction as exposed by the model facade.
 */
sealed class TransactionData {
    /** The transaction name */
    abstract val name: String
    /** An optional comment */
    abstract val comment: String?
    /** The creation timestamp of the transaction */
    abstract val timestamp: Instant
    /** Changes in balance per user */
    abstract val balanceChanges: Map<UserId, BigDec>
    /** Transaction author */
    abstract val originatingUser: UserId

    /**
     * Represents a group payment as exposed by the model facade.
     */
    data class Payment(
        override val name: String,
        override val comment: String?,
        override val timestamp: Instant,
        override val balanceChanges: Map<UserId, BigDec>,
        override val originatingUser: UserId
    ) : TransactionData()

    /**
     * Represents a group expense as exposed by the model facade.
     */
    data class Expense(
        override val name: String,
        override val comment: String?,
        override val timestamp: Instant,
        override val balanceChanges: Map<UserId, BigDec>,
        override val originatingUser: UserId,
        /** Expense total amount */
        val expenseAmount: BigDec
    ) : TransactionData()
}
