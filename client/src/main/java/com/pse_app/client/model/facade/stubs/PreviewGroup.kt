package com.pse_app.client.model.facade.stubs

import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.Transaction
import com.pse_app.client.model.facade.User
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

val stubException = IllegalStateException("Not available on this stub")

open class PreviewGroup(
    name: String,
    balance: BigDec,
) : Group {
    override val id: String = ""
    override val displayName = flowOf<String?>(name)
    override val personalBalance = flowOf(balance)

    override val members: Flow<List<User>?>
        get() = throw stubException
    override val inviteUrl: Flow<String?>
        get() = throw stubException
    override val transactions: Flow<List<Transaction>>
        get() = throw stubException
    override val mostRecentTransaction: Flow<Instant?>
        get() = throw stubException
    override val balances: Flow<Map<User, BigDec>>
        get() = throw stubException

    override suspend fun refreshPartial() {
        throw stubException
    }

    override suspend fun refresh() {
        throw stubException
    }

    override suspend fun setDisplayName(name: String) {
        throw stubException
    }

    override suspend fun kickUser(user: User) {
        throw stubException
    }

    override suspend fun regenerateInviteLink() {
        throw stubException
    }

    override suspend fun addExpense(
        name: String,
        comment: String?,
        split: Map<User, BigDec>,
        originatingUser: User,
        total: BigDec
    ) {
        throw stubException
    }

    override suspend fun addPayment(
        name: String,
        comment: String?,
        externalBalanceChange: Map<User, BigDec>,
        payingUser: User,
        originatingUser: User
    ) {
        throw stubException
    }
}
