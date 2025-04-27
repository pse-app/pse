package com.pse_app.client.model.repositories.remote

import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.common.dto.ExpenseRequestPayload
import com.pse_app.common.dto.ExpenseResponsePayload
import com.pse_app.common.dto.PaymentRequestPayload
import com.pse_app.common.dto.PaymentResponsePayload
import com.pse_app.common.dto.TransactionRequestPayload
import com.pse_app.common.dto.TransactionResponsePayload
import java.util.UUID
import com.pse_app.common.dto.GroupId as DTOGroupId
import com.pse_app.common.dto.UserId as DTOUserId

/**
 * Perform conversion
 */
fun UserId.cast(): DTOUserId {
    return DTOUserId(this.id)
}

/**
 * Perform conversion
 */
fun GroupId.cast(): DTOGroupId {
    return DTOGroupId(UUID.fromString(this.id))
}

/**
 * Perform conversion
 */
fun DTOUserId.cast(): UserId {
    return UserId(this.id)
}

/**
 * Perform conversion
 */
fun DTOGroupId.cast(): GroupId {
    return GroupId(this.toString())
}

internal fun fromDTO(transaction: TransactionResponsePayload): TransactionData {
    return when (transaction) {
        is PaymentResponsePayload -> {
            TransactionData.Payment(
                transaction.name,
                transaction.comment,
                transaction.timestamp,
                transaction.balanceChanges.map { it.key.cast() to it.value }.toMap(),
                transaction.originatingUser.cast()
            )
        }
        is ExpenseResponsePayload -> {
            TransactionData.Expense(
                transaction.name,
                transaction.comment,
                transaction.timestamp,
                transaction.balanceChanges.map { it.key.cast() to it.value }.toMap(),
                transaction.originatingUser.cast(),
                transaction.expenseAmount
            )
        }
    }
}

internal fun toDTO(transactionData: TransactionData): TransactionRequestPayload {
    return when (transactionData) {
        is TransactionData.Payment -> {
            PaymentRequestPayload(
                transactionData.name,
                transactionData.comment,
                transactionData.balanceChanges.map { it.key.cast() to it.value }.toMap(),
            )
        }
        is TransactionData.Expense -> {
            ExpenseRequestPayload(
                transactionData.name,
                transactionData.comment,
                transactionData.balanceChanges.map { it.key.cast() to it.value }.toMap(),
                transactionData.expenseAmount
            )
        }
    }
}
