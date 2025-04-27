package com.pse_app.client.model.repositories.local

import com.pse_app.client.model.repositories.TransactionRepoObservables
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

// Expect around 10 groups
private const val BUFFER_CAPACITY = 10

/**
 * Local transaction repository to be used for caching and testing.
 */
open class LocalTransactionRepo : TransactionRepoObservables {
    private val transactionData = mutableMapOf<GroupId, List<TransactionData>>()
    private val dataChanged = MutableSharedFlow<GroupId?>(extraBufferCapacity = BUFFER_CAPACITY)

    /**
     * Set transactions for group. Validity not checked!
     */
    suspend fun setTransactions(groupId: GroupId, transactions: List<TransactionData>) {
        transactionData[groupId] = transactions
        dataChanged.emit(groupId)
    }

    override fun observeTransactions(groupId: GroupId): Flow<List<TransactionData>?> {
        return dataChanged.toObservable(groupId) { transactionData[groupId] }
    }

    override suspend fun clear() {
        transactionData.clear()
        dataChanged.emit(null)
    }
}
