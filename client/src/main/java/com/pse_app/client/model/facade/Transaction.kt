package com.pse_app.client.model.facade

import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.common.util.BigDec
import java.time.Instant

/**
 * Represents a group transaction as exposed by the model facade.
 */
sealed class Transaction {
    /** The transaction name */
    abstract val name: String
    /** An optional comment */
    abstract val comment: String?
    /** The creation timestamp of the transaction */
    abstract val timestamp: Instant
    /** Changes in balance per user */
    abstract val balanceChanges: Map<User, BigDec>
    /** Transaction author */
    abstract val originatingUser: User

    /**
     * Represents a group payment as exposed by the model facade.
     */
    data class Payment(
        override val name: String,
        override val comment: String?,
        override val timestamp: Instant,
        override val balanceChanges: Map<User, BigDec>,
        override val originatingUser: User
    ) : Transaction()

    /**
     * Represents a group expense as exposed by the model facade.
     */
    data class Expense(
        override val name: String,
        override val comment: String?,
        override val timestamp: Instant,
        override val balanceChanges: Map<User, BigDec>,
        override val originatingUser: User,
        /** Expense total amount */
        val expenseAmount: BigDec
    ) : Transaction()

    /**
     * Convert this to [TransactionData]
     */
    fun toData(): TransactionData {
        return when (this) {
            is Expense -> {
                TransactionData.Expense (
                    name,
                    comment,
                    timestamp,
                    balanceChanges.map { (key, value) -> UserId(key.id) to value }.toMap(),
                    UserId(originatingUser.id),
                    expenseAmount
                )
            }

            is Payment -> {
                TransactionData.Payment (
                    name,
                    comment,
                    timestamp,
                    balanceChanges.map { (key, value) -> UserId(key.id) to value }.toMap(),
                    UserId(originatingUser.id)
                )
            }
        }
    }
}
