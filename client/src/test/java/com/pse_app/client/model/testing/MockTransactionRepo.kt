package com.pse_app.client.model.testing

import com.pse_app.client.model.repositories.TransactionRepo
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.local.LocalTransactionRepo

class MockTransactionRepo: LocalTransactionRepo(), TransactionRepo {
    override suspend fun refreshTransactions(groupId: GroupId) {
        throw NotImplementedError()
    }

    override suspend fun postTransactions(transactions: Map<GroupId, TransactionData>) {
        throw NotImplementedError()
    }
}
