package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * UserRepo actions independent of backend.
 */
interface TransactionRepoObservables {
    /**
     * Get a Flow on the list of [TransactionData] for a group with [GroupId].
     * Flow will emit a first value and then values when the data changes.
     */
    fun observeTransactions(groupId: GroupId): Flow<List<TransactionData>?>

    /**
     * Get the balance of users derived from transaction list.
     */
    suspend fun getDerivedBalances(groupId: GroupId): Map<UserId, BigDec> {
        val balances: MutableMap<UserId, BigDec> = mutableMapOf()
        for (transaction in observeTransactions(groupId).first() ?: listOf()) {
            transaction.balanceChanges
                .forEach { balances[it.key] = (balances[it.key] ?: BigDec(0)) + it.value }
        }
        return balances.toMap()
    }

    /**
     * Delete locally saved state. Use for logout.
     */
    suspend fun clear()
}
